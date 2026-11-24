package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.NamespaceNotFoundException;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.NamespaceModel;
import x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog.IcebergCatalogFactory;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IcebergManagersTest {

    private static final CatalogModel DATA_LAKE = new CatalogModel("data_lake");
    private static final NamespaceModel MARKET_DATA = new NamespaceModel(DATA_LAKE, "market_data");

    @Test
    void tc004ConstructorRejectsNullBlankNameOrNonNamespaceSupportingCatalog() {
        assertThatThrownBy(() -> new IcebergNamespaceManager(null))
                .isInstanceOf(CatalogOperationException.class);
        assertThatThrownBy(() -> new IcebergTableManager(null))
                .isInstanceOf(CatalogOperationException.class);

        Catalog blankNamed = catalogOnlyProxy("   ");
        assertThatThrownBy(() -> new IcebergNamespaceManager(blankNamed))
                .isInstanceOf(CatalogOperationException.class);
        assertThatThrownBy(() -> new IcebergTableManager(blankNamed))
                .isInstanceOf(CatalogOperationException.class);

        Catalog withoutNamespaces = catalogOnlyProxy("data_lake");
        assertThatThrownBy(() -> new IcebergNamespaceManager(withoutNamespaces))
                .isInstanceOf(CatalogOperationException.class)
                .hasMessageContaining("SupportsNamespaces");
        assertThatThrownBy(() -> new IcebergTableManager(withoutNamespaces))
                .isInstanceOf(CatalogOperationException.class)
                .hasMessageContaining("SupportsNamespaces");
    }

    @Test
    void tc005RejectsCatalogNameMismatchWithCatalogNotFoundException() throws URISyntaxException {
        Catalog devCatalog = IcebergCatalogFactory.loadCatalog("in_memory", new CatalogModel("dev_lake"), Map.of());
        IcebergNamespaceManager namespaceManager = new IcebergNamespaceManager(devCatalog);
        IcebergTableManager tableManager = new IcebergTableManager(devCatalog);

        Path namespaceYaml = resourcePath("/namespace.yaml");
        Path tableYaml = resourcePath("/table.yaml");

        assertThatThrownBy(() -> namespaceManager.namespaceExists(namespaceYaml))
                .isInstanceOf(CatalogNotFoundException.class)
                .extracting(e -> ((CatalogNotFoundException) e).getCatalogModel())
                .isEqualTo(DATA_LAKE);
        assertThatThrownBy(() -> namespaceManager.namespaceExists(MARKET_DATA))
                .isInstanceOf(CatalogNotFoundException.class)
                .extracting(e -> ((CatalogNotFoundException) e).getCatalogModel())
                .isEqualTo(DATA_LAKE);
        assertThatThrownBy(() -> namespaceManager.createNamespace(namespaceYaml))
                .isInstanceOf(CatalogNotFoundException.class)
                .extracting(e -> ((CatalogNotFoundException) e).getCatalogModel())
                .isEqualTo(DATA_LAKE);
        assertThatThrownBy(() -> tableManager.createTable(tableYaml))
                .isInstanceOf(CatalogNotFoundException.class)
                .extracting(e -> ((CatalogNotFoundException) e).getCatalogModel())
                .isEqualTo(DATA_LAKE);

        assertThat(((SupportsNamespaces) devCatalog).listNamespaces()).isEmpty();
    }

    @Test
    void tc006ToTc010EndToEndNamespaceAndTableCreationIdempotencyAndUnpartitioned(@TempDir Path tempDir)
            throws URISyntaxException, IOException {
        Catalog catalog = IcebergCatalogFactory.loadCatalog("in_memory", DATA_LAKE, Map.of());
        IcebergNamespaceManager namespaceManager = new IcebergNamespaceManager(catalog);
        IcebergTableManager tableManager = new IcebergTableManager(catalog);

        Path namespaceYaml = resourcePath("/namespace.yaml");
        Path tableYaml = resourcePath("/table.yaml");

        // TC-007: Table creation fails strictly when namespace is missing (never auto-created)
        assertThat(namespaceManager.namespaceExists(namespaceYaml)).isFalse();
        assertThat(namespaceManager.namespaceExists(MARKET_DATA)).isFalse();
        assertThatThrownBy(() -> tableManager.createTable(tableYaml))
                .isInstanceOf(NamespaceNotFoundException.class)
                .extracting(e -> ((NamespaceNotFoundException) e).getNamespaceModel())
                .isEqualTo(MARKET_DATA);
        assertThat(((SupportsNamespaces) catalog).namespaceExists(Namespace.of("market_data"))).isFalse();

        // TC-006: Namespace creation is idempotent and flips both namespaceExists overloads
        assertThatCode(() -> namespaceManager.createNamespace(namespaceYaml)).doesNotThrowAnyException();
        assertThatCode(() -> namespaceManager.createNamespace(namespaceYaml)).doesNotThrowAnyException();
        assertThat(namespaceManager.namespaceExists(namespaceYaml)).isTrue();
        assertThat(namespaceManager.namespaceExists(MARKET_DATA)).isTrue();

        // TC-008 & TC-010: Partitioned table creation happy path + idempotency
        assertThatCode(() -> tableManager.createTable(tableYaml)).doesNotThrowAnyException();
        assertThatCode(() -> tableManager.createTable(tableYaml)).doesNotThrowAnyException();

        TableIdentifier txId = TableIdentifier.of(Namespace.of("market_data"), "transactions");
        assertThat(catalog.tableExists(txId)).isTrue();
        Table transactions = catalog.loadTable(txId);
        assertThat(transactions.schema().columns()).hasSize(3);
        assertThat(transactions.schema().columns().get(0)).satisfies(f -> {
            assertThat(f.fieldId()).isEqualTo(1);
            assertThat(f.name()).isEqualTo("date");
            assertThat(f.type()).isEqualTo(Types.DateType.get());
            assertThat(f.isRequired()).isTrue();
        });
        assertThat(transactions.schema().columns().get(1)).satisfies(f -> {
            assertThat(f.fieldId()).isEqualTo(2);
            assertThat(f.name()).isEqualTo("id");
            assertThat(f.type()).isEqualTo(Types.LongType.get());
            assertThat(f.isRequired()).isTrue();
        });
        assertThat(transactions.schema().columns().get(2)).satisfies(f -> {
            assertThat(f.fieldId()).isEqualTo(3);
            assertThat(f.name()).isEqualTo("amount");
            assertThat(f.type()).isEqualTo(Types.DecimalType.of(38, 9));
            assertThat(f.isOptional()).isTrue();
        });
        assertThat(transactions.spec().isPartitioned()).isTrue();
        assertThat(transactions.spec().fields()).hasSize(1);
        assertThat(transactions.spec().fields().get(0).name()).isEqualTo("date");
        assertThat(transactions.spec().fields().get(0).transform().isIdentity()).isTrue();

        // TC-009: Unpartitioned table with omitted nullable defaults to optional Iceberg field
        Path unpartitionedYaml = tempDir.resolve("quotes.yaml");
        Files.writeString(unpartitionedYaml, """
                catalog: data_lake
                namespace: market_data
                tableName: quotes
                columns:
                  - name: symbol
                    type: STRING
                  - name: event_ts
                    type: TIMESTAMP
                    nullable: false
                """);
        tableManager.createTable(unpartitionedYaml);
        Table quotes = catalog.loadTable(TableIdentifier.of(Namespace.of("market_data"), "quotes"));
        assertThat(quotes.spec().isUnpartitioned()).isTrue();
        assertThat(quotes.schema().findField("symbol").isOptional()).isTrue();
        assertThat(quotes.schema().findField("event_ts").isRequired()).isTrue();
        assertThat(quotes.schema().findField("event_ts").type()).isEqualTo(Types.TimestampType.withZone());
    }

    @Test
    void tc012AbsorbsAlreadyExistsExceptionDuringConcurrentCreation() throws URISyntaxException {
        Path namespaceYaml = resourcePath("/namespace.yaml");
        Path tableYaml = resourcePath("/table.yaml");

        Catalog racingNsCatalog = stubNamespaceCatalog("data_lake", false, false,
                new AlreadyExistsException("namespace created concurrently"), null);
        IcebergNamespaceManager namespaceManager = new IcebergNamespaceManager(racingNsCatalog);
        assertThatCode(() -> namespaceManager.createNamespace(namespaceYaml)).doesNotThrowAnyException();

        Catalog racingTableCatalog = stubNamespaceCatalog("data_lake", true, false,
                null, new AlreadyExistsException("table created concurrently"));
        IcebergTableManager tableManager = new IcebergTableManager(racingTableCatalog);
        assertThatCode(() -> tableManager.createTable(tableYaml)).doesNotThrowAnyException();
    }

    @Test
    void tc013TranslatesNoSuchNamespaceRaceAndWrapsUnexpectedIcebergErrors() throws URISyntaxException {
        Path namespaceYaml = resourcePath("/namespace.yaml");
        Path tableYaml = resourcePath("/table.yaml");

        NoSuchNamespaceException toctouCause = new NoSuchNamespaceException("dropped concurrently");
        Catalog toctouCatalog = stubNamespaceCatalog("data_lake", true, false, null, toctouCause);
        IcebergTableManager toctouTableManager = new IcebergTableManager(toctouCatalog);
        assertThatThrownBy(() -> toctouTableManager.createTable(tableYaml))
                .isInstanceOf(NamespaceNotFoundException.class)
                .hasCause(toctouCause);

        RuntimeException networkBoom = new IllegalStateException("REST 503 unavailable");
        Catalog failingCatalog = failingAllOpsCatalog("data_lake", networkBoom);
        IcebergNamespaceManager failingNsManager = new IcebergNamespaceManager(failingCatalog);
        IcebergTableManager failingTableManager = new IcebergTableManager(failingCatalog);

        assertThatThrownBy(() -> failingNsManager.namespaceExists(MARKET_DATA))
                .isInstanceOf(CatalogOperationException.class)
                .hasCause(networkBoom);
        assertThatThrownBy(() -> failingNsManager.createNamespace(namespaceYaml))
                .isInstanceOf(CatalogOperationException.class)
                .hasCause(networkBoom);
        assertThatThrownBy(() -> failingTableManager.createTable(tableYaml))
                .isInstanceOf(CatalogOperationException.class)
                .hasCause(networkBoom);
    }

    private static Catalog catalogOnlyProxy(String name) {
        return (Catalog) Proxy.newProxyInstance(
                Catalog.class.getClassLoader(),
                new Class<?>[]{Catalog.class},
                (proxy, method, args) -> {
                    if ("name".equals(method.getName())) {
                        return name;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static Catalog stubNamespaceCatalog(
            String name,
            boolean nsExists,
            boolean tblExists,
            RuntimeException onCreateNs,
            RuntimeException onCreateTable) {
        return (Catalog) Proxy.newProxyInstance(
                Catalog.class.getClassLoader(),
                new Class<?>[]{Catalog.class, SupportsNamespaces.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "name" -> name;
                    case "namespaceExists" -> nsExists;
                    case "tableExists" -> tblExists;
                    case "createNamespace" -> {
                        if (onCreateNs != null) {
                            throw onCreateNs;
                        }
                        yield null;
                    }
                    case "createTable" -> {
                        if (onCreateTable != null) {
                            throw onCreateTable;
                        }
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static Catalog failingAllOpsCatalog(String name, RuntimeException failure) {
        return (Catalog) Proxy.newProxyInstance(
                Catalog.class.getClassLoader(),
                new Class<?>[]{Catalog.class, SupportsNamespaces.class},
                (proxy, method, args) -> {
                    if ("name".equals(method.getName())) {
                        return name;
                    }
                    throw failure;
                }
        );
    }

    private static Path resourcePath(String resource) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                IcebergManagersTest.class.getResource(resource),
                "Missing test resource: " + resource
        ).toURI());
    }
}
