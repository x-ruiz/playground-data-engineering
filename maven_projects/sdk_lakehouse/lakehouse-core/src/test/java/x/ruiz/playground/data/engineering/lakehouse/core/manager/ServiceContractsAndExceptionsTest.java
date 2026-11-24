package x.ruiz.playground.data.engineering.lakehouse.core.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidNamespaceSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.LakehouseException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.NamespaceNotFoundException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.SpecParseException;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.CatalogSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.NamespaceSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.TableSpec;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceContractsAndExceptionsTest {

    private static final CatalogSpec DATA_LAKE = new CatalogSpec("data_lake");
    private static final NamespaceSpec MARKET_DATA = new NamespaceSpec(DATA_LAKE, "market_data");

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
    void tc015TemplateManagersEnforcePlatformCatalogNamespaceChecksAndIdempotencyViaYamlPaths()
            throws URISyntaxException {
        Path namespaceYaml = resourcePath("/namespace.yaml");
        Path tableYaml = resourcePath("/table.yaml");

        InMemoryStore store = new InMemoryStore();
        NamespaceManager namespaceManager = new InMemoryNamespaceManager(store);
        TableManager tableManager = new InMemoryTableManager(store);

        // Catalog must be pre-provisioned by the platform; missing catalog must fail and never auto-create
        assertThatThrownBy(() -> namespaceManager.namespaceExists(namespaceYaml))
                .isInstanceOf(CatalogNotFoundException.class)
                .extracting(e -> ((CatalogNotFoundException) e).getCatalogSpec())
                .isEqualTo(DATA_LAKE);
        assertThatThrownBy(() -> namespaceManager.namespaceExists(MARKET_DATA))
                .isInstanceOf(CatalogNotFoundException.class)
                .extracting(e -> ((CatalogNotFoundException) e).getCatalogSpec())
                .isEqualTo(DATA_LAKE);
        assertThatThrownBy(() -> namespaceManager.createNamespace(namespaceYaml))
                .isInstanceOf(CatalogNotFoundException.class)
                .extracting(e -> ((CatalogNotFoundException) e).getCatalogSpec())
                .isEqualTo(DATA_LAKE);
        assertThatThrownBy(() -> tableManager.createTable(tableYaml))
                .isInstanceOf(CatalogNotFoundException.class)
                .extracting(e -> ((CatalogNotFoundException) e).getCatalogSpec())
                .isEqualTo(DATA_LAKE);
        assertThat(store.catalogs).isEmpty();
        assertThat(store.namespaces).isEmpty();
        assertThat(store.tables).isEmpty();

        // Platform provisions the catalog externally
        store.provisionCatalog(DATA_LAKE);

        // Table creation must fail when namespace does not exist
        assertThat(namespaceManager.namespaceExists(namespaceYaml)).isFalse();
        assertThat(namespaceManager.namespaceExists(MARKET_DATA)).isFalse();
        assertThatThrownBy(() -> tableManager.createTable(tableYaml))
                .isInstanceOf(NamespaceNotFoundException.class)
                .extracting(e -> ((NamespaceNotFoundException) e).getNamespaceSpec())
                .isEqualTo(MARKET_DATA);

        // Create namespace via Path: void + idempotent across repeated invocations
        assertThatCode(() -> namespaceManager.createNamespace(namespaceYaml)).doesNotThrowAnyException();
        assertThatCode(() -> namespaceManager.createNamespace(namespaceYaml)).doesNotThrowAnyException();
        assertThat(namespaceManager.namespaceExists(namespaceYaml)).isTrue();
        assertThat(namespaceManager.namespaceExists(MARKET_DATA)).isTrue();
        assertThat(store.namespaces).hasSize(1);

        // Create table via Path: void + idempotent across repeated invocations
        assertThatCode(() -> tableManager.createTable(tableYaml)).doesNotThrowAnyException();
        assertThatCode(() -> tableManager.createTable(tableYaml)).doesNotThrowAnyException();
        assertThat(store.tables).containsExactly("data_lake.market_data.transactions");
    }

    @Test
    void tc018EnforcesPackagePrivateFileSpecManagersAndTemplateMethodModifiers() throws NoSuchMethodException {
        assertThat(FileSpecManager.class.isInterface()).isTrue();
        assertThat(Modifier.isPublic(FileSpecManager.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(YamlFileSpecManager.class.getModifiers())).isFalse();
        assertThat(Modifier.isFinal(YamlFileSpecManager.class.getModifiers())).isTrue();
        assertThat(FileSpecManager.class.isAssignableFrom(YamlFileSpecManager.class)).isTrue();

        assertThat(FileSpecManager.class.getDeclaredMethod("parseTable", Path.class)).isNotNull();
        assertThat(FileSpecManager.class.getDeclaredMethod("parseNamespace", Path.class)).isNotNull();
        assertThat(FileSpecManager.class.getDeclaredMethod("readFile", Path.class, Class.class, String.class))
                .isNotNull();

        assertThat(Modifier.isPublic(TableManager.class.getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(TableManager.class.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(NamespaceManager.class.getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(NamespaceManager.class.getModifiers())).isTrue();

        Method createTable = TableManager.class.getDeclaredMethod("createTable", Path.class);
        assertThat(Modifier.isPublic(createTable.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(createTable.getModifiers())).isTrue();

        Method doCreateTable = TableManager.class.getDeclaredMethod("doCreateTable", TableSpec.class);
        assertThat(Modifier.isProtected(doCreateTable.getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(doCreateTable.getModifiers())).isTrue();

        Method createNs = NamespaceManager.class.getDeclaredMethod("createNamespace", Path.class);
        assertThat(Modifier.isPublic(createNs.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(createNs.getModifiers())).isTrue();

        Method nsExistsPath = NamespaceManager.class.getDeclaredMethod("namespaceExists", Path.class);
        assertThat(Modifier.isPublic(nsExistsPath.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(nsExistsPath.getModifiers())).isTrue();

        Method nsExistsSpec = NamespaceManager.class.getDeclaredMethod("namespaceExists", NamespaceSpec.class);
        assertThat(Modifier.isPublic(nsExistsSpec.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(nsExistsSpec.getModifiers())).isTrue();

        Method doCreateNs = NamespaceManager.class.getDeclaredMethod("doCreateNamespace", NamespaceSpec.class);
        assertThat(Modifier.isProtected(doCreateNs.getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(doCreateNs.getModifiers())).isTrue();

        Method doNsExists = NamespaceManager.class.getDeclaredMethod("doNamespaceExists", NamespaceSpec.class);
        assertThat(Modifier.isProtected(doNsExists.getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(doNsExists.getModifiers())).isTrue();
    }

    @Test
    void tc019ParseSemanticAndNullFailuresNeverInvokeSubclassHooks(@TempDir Path tempDir) throws IOException {
        AtomicInteger tableHookCalls = new AtomicInteger();
        AtomicInteger namespaceCreateCalls = new AtomicInteger();
        AtomicInteger namespaceExistsCalls = new AtomicInteger();

        TableManager spyTableManager = new TableManager() {
            @Override
            protected void doCreateTable(TableSpec spec) {
                tableHookCalls.incrementAndGet();
            }
        };

        NamespaceManager spyNamespaceManager = new NamespaceManager() {
            @Override
            protected void doCreateNamespace(NamespaceSpec spec) {
                namespaceCreateCalls.incrementAndGet();
            }

            @Override
            protected boolean doNamespaceExists(NamespaceSpec spec) {
                namespaceExistsCalls.incrementAndGet();
                return false;
            }
        };

        Path missing = tempDir.resolve("missing.yaml");
        Path malformed = tempDir.resolve("malformed.yaml");
        Files.writeString(malformed, "catalog: [broken");
        Path badCatalog = tempDir.resolve("bad_catalog.yaml");
        Files.writeString(badCatalog, """
                catalog: bad.catalog
                namespace: market_data
                """);
        Path badTable = tempDir.resolve("bad_table.yaml");
        Files.writeString(badTable, """
                catalog: data_lake
                namespace: market_data
                tableName: transactions
                columns:
                  - name: id
                    type: INVALID_TYPE
                """);

        // Null path, missing, malformed, and semantically invalid YAML must never reach subclass hooks
        assertThatThrownBy(() -> spyTableManager.createTable(null)).isInstanceOf(SpecParseException.class);
        assertThatThrownBy(() -> spyTableManager.createTable(missing)).isInstanceOf(SpecParseException.class);
        assertThatThrownBy(() -> spyTableManager.createTable(malformed)).isInstanceOf(SpecParseException.class);
        assertThatThrownBy(() -> spyTableManager.createTable(badTable)).isInstanceOf(InvalidTableSpecException.class);

        assertThatThrownBy(() -> spyNamespaceManager.createNamespace(null)).isInstanceOf(SpecParseException.class);
        assertThatThrownBy(() -> spyNamespaceManager.createNamespace(missing)).isInstanceOf(SpecParseException.class);
        assertThatThrownBy(() -> spyNamespaceManager.createNamespace(malformed)).isInstanceOf(SpecParseException.class);
        assertThatThrownBy(() -> spyNamespaceManager.createNamespace(badCatalog))
                .isInstanceOf(InvalidCatalogSpecException.class);

        assertThatThrownBy(() -> spyNamespaceManager.namespaceExists((Path) null))
                .isInstanceOf(SpecParseException.class);
        assertThatThrownBy(() -> spyNamespaceManager.namespaceExists(missing))
                .isInstanceOf(SpecParseException.class);
        assertThatThrownBy(() -> spyNamespaceManager.namespaceExists((NamespaceSpec) null))
                .isInstanceOf(InvalidNamespaceSpecException.class);

        assertThat(tableHookCalls).hasValue(0);
        assertThat(namespaceCreateCalls).hasValue(0);
        assertThat(namespaceExistsCalls).hasValue(0);

        // Package-private constructors reject null FileSpecManager and guard null parsed specs
        assertThatThrownBy(() -> new TableManager(null) {
            @Override
            protected void doCreateTable(TableSpec spec) {
            }
        }).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new NamespaceManager(null) {
            @Override
            protected void doCreateNamespace(NamespaceSpec spec) {
            }

            @Override
            protected boolean doNamespaceExists(NamespaceSpec spec) {
                return false;
            }
        }).isInstanceOf(IllegalArgumentException.class);

        FileSpecManager nullReturningFileManager = new FileSpecManager() {
            @Override
            public TableSpec parseTable(Path sourcePath) {
                return null;
            }

            @Override
            public NamespaceSpec parseNamespace(Path sourcePath) {
                return null;
            }

            @Override
            public <D> D readFile(Path sourcePath, Class<D> targetType, String specKind) {
                return null;
            }
        };

        TableManager nullReturningTableManager = new TableManager(nullReturningFileManager) {
            @Override
            protected void doCreateTable(TableSpec spec) {
                tableHookCalls.incrementAndGet();
            }
        };
        NamespaceManager nullReturningNamespaceManager = new NamespaceManager(nullReturningFileManager) {
            @Override
            protected void doCreateNamespace(NamespaceSpec spec) {
                namespaceCreateCalls.incrementAndGet();
            }

            @Override
            protected boolean doNamespaceExists(NamespaceSpec spec) {
                namespaceExistsCalls.incrementAndGet();
                return false;
            }
        };

        assertThatThrownBy(() -> nullReturningTableManager.createTable(missing))
                .isInstanceOf(InvalidTableSpecException.class);
        assertThatThrownBy(() -> nullReturningNamespaceManager.createNamespace(missing))
                .isInstanceOf(InvalidNamespaceSpecException.class);
        assertThatThrownBy(() -> nullReturningNamespaceManager.namespaceExists(missing))
                .isInstanceOf(InvalidNamespaceSpecException.class);

        assertThat(tableHookCalls).hasValue(0);
        assertThat(namespaceCreateCalls).hasValue(0);
        assertThat(namespaceExistsCalls).hasValue(0);
    }

    private static Path resourcePath(String resource) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                ServiceContractsAndExceptionsTest.class.getResource(resource),
                "Missing test resource: " + resource
        ).toURI());
    }

    private static final class InMemoryStore {
        private final Set<String> catalogs = new HashSet<>();
        private final Set<String> namespaces = new HashSet<>();
        private final Set<String> tables = new HashSet<>();

        void provisionCatalog(CatalogSpec catalog) {
            catalogs.add(catalog.fullyQualifiedName());
        }

        void requireCatalog(CatalogSpec catalog) {
            if (!catalogs.contains(catalog.fullyQualifiedName())) {
                throw new CatalogNotFoundException(catalog);
            }
        }
    }

    private static final class InMemoryNamespaceManager extends NamespaceManager {
        private final InMemoryStore store;

        InMemoryNamespaceManager(InMemoryStore store) {
            this.store = store;
        }

        @Override
        protected void doCreateNamespace(NamespaceSpec spec) {
            store.requireCatalog(spec.catalog());
            store.namespaces.add(spec.fullyQualifiedName());
        }

        @Override
        protected boolean doNamespaceExists(NamespaceSpec spec) {
            store.requireCatalog(spec.catalog());
            return store.namespaces.contains(spec.fullyQualifiedName());
        }
    }

    private static final class InMemoryTableManager extends TableManager {
        private final InMemoryStore store;

        InMemoryTableManager(InMemoryStore store) {
            this.store = store;
        }

        @Override
        protected void doCreateTable(TableSpec spec) {
            store.requireCatalog(spec.catalog());
            if (!store.namespaces.contains(spec.namespace().fullyQualifiedName())) {
                throw new NamespaceNotFoundException(spec.namespace());
            }
            store.tables.add(spec.fullyQualifiedName());
        }
    }
}
