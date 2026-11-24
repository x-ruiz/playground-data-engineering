package x.ruiz.playground.data.engineering.lakehouse.core.manager;

import org.junit.jupiter.api.Test;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidNamespaceSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.LakehouseException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.NamespaceNotFoundException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.SpecParseException;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.CatalogSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.ColumnSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.ColumnType;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.NamespaceSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.TableSpec;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceContractsAndExceptionsTest {

    private static final CatalogSpec DATA_LAKE = new CatalogSpec("data_lake");
    private static final NamespaceSpec MARKET_DATA = new NamespaceSpec(DATA_LAKE, "market_data");
    private static final TableSpec TRANSACTIONS = new TableSpec(
            MARKET_DATA,
            "transactions",
            List.of(
                    new ColumnSpec("date", ColumnType.DATE, false),
                    new ColumnSpec("id", ColumnType.BIGINT, false)
            ),
            "date"
    );

    @Test
    void tc014NotFoundExceptionsAndExceptionHierarchyPreserveMetadataAndCauses() {
        CatalogSpec missingCatalog = new CatalogSpec("missing_catalog");
        NamespaceSpec missingNs = new NamespaceSpec(DATA_LAKE, "missing_ns");
        RuntimeException rootCause = new RuntimeException("404 from REST catalog");

        CatalogNotFoundException catalogNotFound = new CatalogNotFoundException(missingCatalog);
        assertThat(catalogNotFound).isInstanceOf(LakehouseException.class);
        assertThat(catalogNotFound.getCatalogSpec()).isEqualTo(missingCatalog);
        assertThat(catalogNotFound.getFullyQualifiedName()).isEqualTo("missing_catalog");
        assertThat(catalogNotFound.getMessage()).contains("missing_catalog");

        CatalogNotFoundException catalogNotFoundWithCause = new CatalogNotFoundException(missingCatalog, rootCause);
        assertThat(catalogNotFoundWithCause.getCause()).isSameAs(rootCause);
        assertThat(catalogNotFoundWithCause.getFullyQualifiedName()).isEqualTo("missing_catalog");

        assertThatThrownBy(() -> new CatalogNotFoundException(null))
                .isInstanceOf(InvalidCatalogSpecException.class);

        NamespaceNotFoundException ex = new NamespaceNotFoundException(missingNs);
        assertThat(ex).isInstanceOf(LakehouseException.class);
        assertThat(ex.getNamespaceSpec()).isEqualTo(missingNs);
        assertThat(ex.getFullyQualifiedName()).isEqualTo("data_lake.missing_ns");
        assertThat(ex.getMessage()).contains("data_lake.missing_ns");

        NamespaceNotFoundException exWithCause = new NamespaceNotFoundException(missingNs, rootCause);
        assertThat(exWithCause.getCause()).isSameAs(rootCause);
        assertThat(exWithCause.getFullyQualifiedName()).isEqualTo("data_lake.missing_ns");

        assertThatThrownBy(() -> new NamespaceNotFoundException(null))
                .isInstanceOf(InvalidNamespaceSpecException.class);

        IOException ioCause = new IOException("connection reset");
        CatalogOperationException catalogEx = new CatalogOperationException("Catalog unreachable", ioCause);
        assertThat(catalogEx).isInstanceOf(LakehouseException.class);
        assertThat(catalogEx.getCause()).isSameAs(ioCause);

        SpecParseException parseEx = new SpecParseException("Bad YAML", ioCause);
        assertThat(parseEx).isInstanceOf(LakehouseException.class);
        assertThat(parseEx.getCause()).isSameAs(ioCause);
    }

    @Test
    void tc015ServiceContractsEnforcePlatformCatalogIdempotencyAndStrictNamespaceChecks() {
        InMemoryLakehouseManager manager = new InMemoryLakehouseManager();
        NamespaceManager namespaceManager = manager;
        TableManager tableManager = manager;

        assertThatThrownBy(() -> namespaceManager.createNamespace(null))
                .isInstanceOf(InvalidNamespaceSpecException.class);
        assertThatThrownBy(() -> namespaceManager.namespaceExists(null))
                .isInstanceOf(InvalidNamespaceSpecException.class);
        assertThatThrownBy(() -> tableManager.createTable(null))
                .isInstanceOf(InvalidTableSpecException.class);

        // Catalog must be pre-provisioned by the platform; missing catalog must fail and never auto-create
        assertThatThrownBy(() -> namespaceManager.namespaceExists(MARKET_DATA))
                .isInstanceOf(CatalogNotFoundException.class)
                .extracting(e -> ((CatalogNotFoundException) e).getCatalogSpec())
                .isEqualTo(DATA_LAKE);
        assertThatThrownBy(() -> namespaceManager.createNamespace(MARKET_DATA))
                .isInstanceOf(CatalogNotFoundException.class)
                .extracting(e -> ((CatalogNotFoundException) e).getCatalogSpec())
                .isEqualTo(DATA_LAKE);
        assertThatThrownBy(() -> tableManager.createTable(TRANSACTIONS))
                .isInstanceOf(CatalogNotFoundException.class)
                .extracting(e -> ((CatalogNotFoundException) e).getCatalogSpec())
                .isEqualTo(DATA_LAKE);
        assertThat(manager.catalogCount()).isZero();
        assertThat(manager.namespaceCount()).isZero();
        assertThat(manager.tableCount()).isZero();

        // Platform provisions the catalog externally
        manager.provisionCatalog(DATA_LAKE);

        // Table creation must fail when namespace does not exist
        assertThat(namespaceManager.namespaceExists(MARKET_DATA)).isFalse();
        assertThatThrownBy(() -> tableManager.createTable(TRANSACTIONS))
                .isInstanceOf(NamespaceNotFoundException.class)
                .extracting(e -> ((NamespaceNotFoundException) e).getNamespaceSpec())
                .isEqualTo(MARKET_DATA);

        // Create namespace: void + idempotent across repeated invocations
        assertThatCode(() -> namespaceManager.createNamespace(MARKET_DATA)).doesNotThrowAnyException();
        assertThatCode(() -> namespaceManager.createNamespace(MARKET_DATA)).doesNotThrowAnyException();
        assertThat(namespaceManager.namespaceExists(MARKET_DATA)).isTrue();
        assertThat(manager.namespaceCount()).isEqualTo(1);

        // Create table: void + idempotent across repeated invocations
        assertThatCode(() -> tableManager.createTable(TRANSACTIONS)).doesNotThrowAnyException();
        assertThatCode(() -> tableManager.createTable(TRANSACTIONS)).doesNotThrowAnyException();
        assertThat(manager.tableCount()).isEqualTo(1);
    }

    private static final class InMemoryLakehouseManager implements NamespaceManager, TableManager {
        private final Set<String> catalogs = new HashSet<>();
        private final Set<String> namespaces = new HashSet<>();
        private final Set<String> tables = new HashSet<>();

        void provisionCatalog(CatalogSpec catalog) {
            catalogs.add(catalog.fullyQualifiedName());
        }

        @Override
        public void createNamespace(NamespaceSpec spec) {
            if (spec == null) {
                throw new InvalidNamespaceSpecException("Namespace spec must not be null");
            }
            requireCatalog(spec.catalog());
            namespaces.add(spec.fullyQualifiedName());
        }

        @Override
        public boolean namespaceExists(NamespaceSpec spec) {
            if (spec == null) {
                throw new InvalidNamespaceSpecException("Namespace spec must not be null");
            }
            requireCatalog(spec.catalog());
            return namespaces.contains(spec.fullyQualifiedName());
        }

        @Override
        public void createTable(TableSpec spec) {
            if (spec == null) {
                throw new InvalidTableSpecException("Table spec must not be null");
            }
            requireCatalog(spec.catalog());
            if (!namespaces.contains(spec.namespace().fullyQualifiedName())) {
                throw new NamespaceNotFoundException(spec.namespace());
            }
            tables.add(spec.fullyQualifiedName());
        }

        private void requireCatalog(CatalogSpec catalog) {
            if (!catalogs.contains(catalog.fullyQualifiedName())) {
                throw new CatalogNotFoundException(catalog);
            }
        }

        int catalogCount() {
            return catalogs.size();
        }

        int namespaceCount() {
            return namespaces.size();
        }

        int tableCount() {
            return tables.size();
        }
    }
}
