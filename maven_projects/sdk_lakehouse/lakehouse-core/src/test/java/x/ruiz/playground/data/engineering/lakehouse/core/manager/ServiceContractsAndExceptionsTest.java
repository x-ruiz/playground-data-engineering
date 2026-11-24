package x.ruiz.playground.data.engineering.lakehouse.core.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidNamespaceModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.LakehouseException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.NamespaceNotFoundException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.ModelParseException;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.NamespaceModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.TableModel;

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

    private static final CatalogModel DATA_LAKE = new CatalogModel("data_lake");
    private static final NamespaceModel MARKET_DATA = new NamespaceModel(DATA_LAKE, "market_data");

    @Test
    void tc014NotFoundExceptionsAndExceptionHierarchyPreserveMetadataAndCauses() {
        CatalogModel missingCatalog = new CatalogModel("missing_catalog");
        NamespaceModel missingNs = new NamespaceModel(DATA_LAKE, "missing_ns");
        RuntimeException rootCause = new RuntimeException("404 from REST catalog");

        CatalogNotFoundException catalogNotFound = new CatalogNotFoundException(missingCatalog);
        assertThat(catalogNotFound).isInstanceOf(LakehouseException.class);
        assertThat(catalogNotFound.getCatalogModel()).isEqualTo(missingCatalog);
        assertThat(catalogNotFound.getFullyQualifiedName()).isEqualTo("missing_catalog");
        assertThat(catalogNotFound.getMessage()).contains("missing_catalog");

        CatalogNotFoundException catalogNotFoundWithCause = new CatalogNotFoundException(missingCatalog, rootCause);
        assertThat(catalogNotFoundWithCause.getCause()).isSameAs(rootCause);
        assertThat(catalogNotFoundWithCause.getFullyQualifiedName()).isEqualTo("missing_catalog");

        assertThatThrownBy(() -> new CatalogNotFoundException(null))
                .isInstanceOf(InvalidCatalogModelException.class);

        NamespaceNotFoundException ex = new NamespaceNotFoundException(missingNs);
        assertThat(ex).isInstanceOf(LakehouseException.class);
        assertThat(ex.getNamespaceModel()).isEqualTo(missingNs);
        assertThat(ex.getFullyQualifiedName()).isEqualTo("data_lake.missing_ns");
        assertThat(ex.getMessage()).contains("data_lake.missing_ns");

        NamespaceNotFoundException exWithCause = new NamespaceNotFoundException(missingNs, rootCause);
        assertThat(exWithCause.getCause()).isSameAs(rootCause);
        assertThat(exWithCause.getFullyQualifiedName()).isEqualTo("data_lake.missing_ns");

        assertThatThrownBy(() -> new NamespaceNotFoundException(null))
                .isInstanceOf(InvalidNamespaceModelException.class);

        IOException ioCause = new IOException("connection reset");
        CatalogOperationException catalogEx = new CatalogOperationException("Catalog unreachable", ioCause);
        assertThat(catalogEx).isInstanceOf(LakehouseException.class);
        assertThat(catalogEx.getCause()).isSameAs(ioCause);

        ModelParseException parseEx = new ModelParseException("Bad YAML", ioCause);
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
                .extracting(e -> ((NamespaceNotFoundException) e).getNamespaceModel())
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
    void tc018EnforcesPackagePrivateFileModelManagersAndTemplateMethodModifiers() throws NoSuchMethodException {
        assertThat(FileModelManager.class.isInterface()).isTrue();
        assertThat(Modifier.isPublic(FileModelManager.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(YamlFileModelManager.class.getModifiers())).isFalse();
        assertThat(Modifier.isFinal(YamlFileModelManager.class.getModifiers())).isTrue();
        assertThat(FileModelManager.class.isAssignableFrom(YamlFileModelManager.class)).isTrue();

        assertThat(FileModelManager.class.getDeclaredMethod("parseTable", Path.class)).isNotNull();
        assertThat(FileModelManager.class.getDeclaredMethod("parseNamespace", Path.class)).isNotNull();
        assertThat(FileModelManager.class.getDeclaredMethod("readFile", Path.class, Class.class, String.class))
                .isNotNull();

        assertThat(Modifier.isPublic(TableManager.class.getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(TableManager.class.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(NamespaceManager.class.getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(NamespaceManager.class.getModifiers())).isTrue();

        Method createTable = TableManager.class.getDeclaredMethod("createTable", Path.class);
        assertThat(Modifier.isPublic(createTable.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(createTable.getModifiers())).isTrue();

        Method doCreateTable = TableManager.class.getDeclaredMethod("doCreateTable", TableModel.class);
        assertThat(Modifier.isProtected(doCreateTable.getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(doCreateTable.getModifiers())).isTrue();

        Method createNs = NamespaceManager.class.getDeclaredMethod("createNamespace", Path.class);
        assertThat(Modifier.isPublic(createNs.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(createNs.getModifiers())).isTrue();

        Method nsExistsPath = NamespaceManager.class.getDeclaredMethod("namespaceExists", Path.class);
        assertThat(Modifier.isPublic(nsExistsPath.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(nsExistsPath.getModifiers())).isTrue();

        Method nsExistsSpec = NamespaceManager.class.getDeclaredMethod("namespaceExists", NamespaceModel.class);
        assertThat(Modifier.isPublic(nsExistsSpec.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(nsExistsSpec.getModifiers())).isTrue();

        Method doCreateNs = NamespaceManager.class.getDeclaredMethod("doCreateNamespace", NamespaceModel.class);
        assertThat(Modifier.isProtected(doCreateNs.getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(doCreateNs.getModifiers())).isTrue();

        Method doNsExists = NamespaceManager.class.getDeclaredMethod("doNamespaceExists", NamespaceModel.class);
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
            protected void doCreateTable(TableModel spec) {
                tableHookCalls.incrementAndGet();
            }
        };

        NamespaceManager spyNamespaceManager = new NamespaceManager() {
            @Override
            protected void doCreateNamespace(NamespaceModel spec) {
                namespaceCreateCalls.incrementAndGet();
            }

            @Override
            protected boolean doNamespaceExists(NamespaceModel spec) {
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
        assertThatThrownBy(() -> spyTableManager.createTable(null)).isInstanceOf(ModelParseException.class);
        assertThatThrownBy(() -> spyTableManager.createTable(missing)).isInstanceOf(ModelParseException.class);
        assertThatThrownBy(() -> spyTableManager.createTable(malformed)).isInstanceOf(ModelParseException.class);
        assertThatThrownBy(() -> spyTableManager.createTable(badTable)).isInstanceOf(InvalidTableModelException.class);

        assertThatThrownBy(() -> spyNamespaceManager.createNamespace(null)).isInstanceOf(ModelParseException.class);
        assertThatThrownBy(() -> spyNamespaceManager.createNamespace(missing)).isInstanceOf(ModelParseException.class);
        assertThatThrownBy(() -> spyNamespaceManager.createNamespace(malformed)).isInstanceOf(ModelParseException.class);
        assertThatThrownBy(() -> spyNamespaceManager.createNamespace(badCatalog))
                .isInstanceOf(InvalidCatalogModelException.class);

        assertThatThrownBy(() -> spyNamespaceManager.namespaceExists((Path) null))
                .isInstanceOf(ModelParseException.class);
        assertThatThrownBy(() -> spyNamespaceManager.namespaceExists(missing))
                .isInstanceOf(ModelParseException.class);
        assertThatThrownBy(() -> spyNamespaceManager.namespaceExists((NamespaceModel) null))
                .isInstanceOf(InvalidNamespaceModelException.class);

        assertThat(tableHookCalls).hasValue(0);
        assertThat(namespaceCreateCalls).hasValue(0);
        assertThat(namespaceExistsCalls).hasValue(0);

        // Package-private constructors reject null FileModelManager and guard null parsed specs
        assertThatThrownBy(() -> new TableManager(null) {
            @Override
            protected void doCreateTable(TableModel spec) {
            }
        }).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new NamespaceManager(null) {
            @Override
            protected void doCreateNamespace(NamespaceModel spec) {
            }

            @Override
            protected boolean doNamespaceExists(NamespaceModel spec) {
                return false;
            }
        }).isInstanceOf(IllegalArgumentException.class);

        FileModelManager nullReturningFileManager = new FileModelManager() {
            @Override
            public TableModel parseTable(Path sourcePath) {
                return null;
            }

            @Override
            public NamespaceModel parseNamespace(Path sourcePath) {
                return null;
            }

            @Override
            public <D> D readFile(Path sourcePath, Class<D> targetType, String modelKind) {
                return null;
            }
        };

        TableManager nullReturningTableManager = new TableManager(nullReturningFileManager) {
            @Override
            protected void doCreateTable(TableModel spec) {
                tableHookCalls.incrementAndGet();
            }
        };
        NamespaceManager nullReturningNamespaceManager = new NamespaceManager(nullReturningFileManager) {
            @Override
            protected void doCreateNamespace(NamespaceModel spec) {
                namespaceCreateCalls.incrementAndGet();
            }

            @Override
            protected boolean doNamespaceExists(NamespaceModel spec) {
                namespaceExistsCalls.incrementAndGet();
                return false;
            }
        };

        assertThatThrownBy(() -> nullReturningTableManager.createTable(missing))
                .isInstanceOf(InvalidTableModelException.class);
        assertThatThrownBy(() -> nullReturningNamespaceManager.createNamespace(missing))
                .isInstanceOf(InvalidNamespaceModelException.class);
        assertThatThrownBy(() -> nullReturningNamespaceManager.namespaceExists(missing))
                .isInstanceOf(InvalidNamespaceModelException.class);

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

        void provisionCatalog(CatalogModel catalog) {
            catalogs.add(catalog.fullyQualifiedName());
        }

        void requireCatalog(CatalogModel catalog) {
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
        protected void doCreateNamespace(NamespaceModel spec) {
            store.requireCatalog(spec.catalog());
            store.namespaces.add(spec.fullyQualifiedName());
        }

        @Override
        protected boolean doNamespaceExists(NamespaceModel spec) {
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
        protected void doCreateTable(TableModel spec) {
            store.requireCatalog(spec.catalog());
            if (!store.namespaces.contains(spec.namespace().fullyQualifiedName())) {
                throw new NamespaceNotFoundException(spec.namespace());
            }
            store.tables.add(spec.fullyQualifiedName());
        }
    }
}
