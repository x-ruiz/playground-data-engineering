package x.ruiz.playground.data.engineering.lakehouse.core.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidNamespaceModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.ModelParseException;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.ColumnModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.ColumnType;
import x.ruiz.playground.data.engineering.lakehouse.core.model.NamespaceModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.TableModel;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlFileModelManagerTest {

    private final FileModelManager fileModelManager = YamlFileModelManager.INSTANCE;

    @Test
    void tc001ParsesValidTableYamlFixture() throws URISyntaxException {
        Path fixture = resourcePath("/table.yaml");

        TableModel spec = fileModelManager.parseTable(fixture);

        assertThat(spec.fullyQualifiedName()).isEqualTo("data_lake.market_data.transactions");
        assertThat(spec.catalog()).isEqualTo(new CatalogModel("data_lake"));
        assertThat(spec.catalog().fullyQualifiedName()).isEqualTo("data_lake");
        assertThat(spec.namespace()).isEqualTo(new NamespaceModel(new CatalogModel("data_lake"), "market_data"));
        assertThat(spec.namespace().fullyQualifiedName()).isEqualTo("data_lake.market_data");
        assertThat(spec.name()).isEqualTo("transactions");
        assertThat(spec.isPartitioned()).isTrue();
        assertThat(spec.partitionColumn()).isEqualTo("date");
        assertThat(spec.partitionColumnOptional()).contains("date");
        assertThat(spec.columns()).containsExactly(
                new ColumnModel("date", ColumnType.DATE, false),
                new ColumnModel("id", ColumnType.BIGINT, false),
                new ColumnModel("amount", ColumnType.DECIMAL, true)
        );
    }

    @Test
    void tc002SupportsUnpartitionedTableAndDefaultsOmittedNullableToTrue(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("unpartitioned.yaml");
        Files.writeString(yaml, """
                catalog: data_lake
                namespace: market_data
                tableName: quotes
                columns:
                  - name: symbol
                    type: STRING
                  - name: price
                    type: DOUBLE
                    nullable: false
                """);

        TableModel spec = fileModelManager.parseTable(yaml);

        assertThat(spec.isPartitioned()).isFalse();
        assertThat(spec.partitionColumnOptional()).isEmpty();
        assertThat(spec.columns()).containsExactly(
                new ColumnModel("symbol", ColumnType.STRING, true),
                new ColumnModel("price", ColumnType.DOUBLE, false)
        );
    }

    @Test
    void tc003ParsesCaseInsensitiveAndWhitespaceTrimmedColumnTypesFromYaml(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("case_types.yaml");
        Files.writeString(yaml, """
                catalog: data_lake
                namespace: market_data
                tableName: events
                columns:
                  - name: event_date
                    type: " date "
                  - name: event_id
                    type: bigint
                """);

        TableModel spec = fileModelManager.parseTable(yaml);

        assertThat(spec.columns()).containsExactly(
                new ColumnModel("event_date", ColumnType.DATE, true),
                new ColumnModel("event_id", ColumnType.BIGINT, true)
        );
    }

    @Test
    void tc004RejectsInvalidColumnTypeInYaml(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("bad_type.yaml");
        Files.writeString(yaml, """
                catalog: data_lake
                namespace: market_data
                tableName: bad_types
                columns:
                  - name: col1
                    type: VARCHR
                """);

        assertThatThrownBy(() -> fileModelManager.parseTable(yaml))
                .isInstanceOf(InvalidTableModelException.class)
                .hasMessageContaining("VARCHR");
    }

    @Test
    void tc005PropagatesSemanticValidationErrorsForInvalidCatalogAndNamespace(@TempDir Path tempDir) throws IOException {
        Path badCatalog = tempDir.resolve("bad_catalog.yaml");
        Files.writeString(badCatalog, """
                catalog: bad.catalog
                namespace: market_data
                tableName: transactions
                columns:
                  - name: id
                    type: BIGINT
                """);
        assertThatThrownBy(() -> fileModelManager.parseTable(badCatalog))
                .isInstanceOf(InvalidCatalogModelException.class);

        Path badNamespace = tempDir.resolve("bad_namespace.yaml");
        Files.writeString(badNamespace, """
                catalog: data_lake
                namespace: bad namespace
                tableName: transactions
                columns:
                  - name: id
                    type: BIGINT
                """);
        assertThatThrownBy(() -> fileModelManager.parseTable(badNamespace))
                .isInstanceOf(InvalidNamespaceModelException.class);
    }

    @Test
    void tc007AndTc008RejectsNullColumnEntriesAndInvalidPartitionColumnInYaml(@TempDir Path tempDir) throws IOException {
        Path nullColumnEntry = tempDir.resolve("null_col.yaml");
        Files.writeString(nullColumnEntry, """
                catalog: data_lake
                namespace: market_data
                tableName: transactions
                columns:
                  - name: id
                    type: BIGINT
                  - null
                """);
        assertThatThrownBy(() -> fileModelManager.parseTable(nullColumnEntry))
                .isInstanceOf(InvalidTableModelException.class);

        Path badPartition = tempDir.resolve("bad_partition.yaml");
        Files.writeString(badPartition, """
                catalog: data_lake
                namespace: market_data
                tableName: transactions
                columns:
                  - name: id
                    type: BIGINT
                partitionColumn: missing_col
                """);
        assertThatThrownBy(() -> fileModelManager.parseTable(badPartition))
                .isInstanceOf(InvalidTableModelException.class)
                .hasMessageContaining("missing_col");
    }

    @Test
    void tc009RejectsUnknownYamlPropertyWithJacksonCause(@TempDir Path tempDir) throws IOException {
        Path typoYaml = tempDir.resolve("typo.yaml");
        Files.writeString(typoYaml, """
                catalog: data_lake
                namespace: market_data
                tableName: transactions
                columns:
                  - name: date
                    type: DATE
                partitonColumn: date
                """);

        assertThatThrownBy(() -> fileModelManager.parseTable(typoYaml))
                .isInstanceOf(ModelParseException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void tc010RejectsNullMissingEmptyCommentOnlyAndMalformedYaml(@TempDir Path tempDir) throws IOException {
        Path missing = tempDir.resolve("does_not_exist.yaml");
        Path empty = tempDir.resolve("empty.yaml");
        Files.writeString(empty, "");
        Path commentOnly = tempDir.resolve("comments.yaml");
        Files.writeString(commentOnly, "# only a comment\n---\n");
        Path malformed = tempDir.resolve("malformed.yaml");
        Files.writeString(malformed, "catalog: [unclosed\nnamespace: : :");

        assertThatThrownBy(() -> fileModelManager.parseTable(null)).isInstanceOf(ModelParseException.class);
        assertThatThrownBy(() -> fileModelManager.parseTable(missing))
                .isInstanceOf(ModelParseException.class)
                .hasCauseInstanceOf(IOException.class);
        assertThatThrownBy(() -> fileModelManager.parseTable(empty)).isInstanceOf(ModelParseException.class);
        assertThatThrownBy(() -> fileModelManager.parseTable(commentOnly)).isInstanceOf(ModelParseException.class);
        assertThatThrownBy(() -> fileModelManager.parseTable(malformed))
                .isInstanceOf(ModelParseException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);

        assertThatThrownBy(() -> fileModelManager.parseNamespace(null)).isInstanceOf(ModelParseException.class);
        assertThatThrownBy(() -> fileModelManager.parseNamespace(missing))
                .isInstanceOf(ModelParseException.class)
                .hasCauseInstanceOf(IOException.class);
        assertThatThrownBy(() -> fileModelManager.parseNamespace(empty)).isInstanceOf(ModelParseException.class);
        assertThatThrownBy(() -> fileModelManager.parseNamespace(commentOnly)).isInstanceOf(ModelParseException.class);
        assertThatThrownBy(() -> fileModelManager.parseNamespace(malformed))
                .isInstanceOf(ModelParseException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void tc011ParsesValidNamespaceYamlFixture() throws URISyntaxException {
        Path fixture = resourcePath("/namespace.yaml");

        NamespaceModel spec = fileModelManager.parseNamespace(fixture);

        assertThat(spec.catalog()).isEqualTo(new CatalogModel("data_lake"));
        assertThat(spec.catalog().fullyQualifiedName()).isEqualTo("data_lake");
        assertThat(spec.name()).isEqualTo("market_data");
        assertThat(spec.fullyQualifiedName()).isEqualTo("data_lake.market_data");
    }

    @Test
    void tc012RejectsInvalidNamespaceYamlSemanticAndUnknownKeyErrors(@TempDir Path tempDir) throws IOException {
        Path missingCatalog = tempDir.resolve("missing_catalog.yaml");
        Files.writeString(missingCatalog, """
                namespace: market_data
                """);
        assertThatThrownBy(() -> fileModelManager.parseNamespace(missingCatalog))
                .isInstanceOf(InvalidCatalogModelException.class);

        Path missingNamespace = tempDir.resolve("missing_ns.yaml");
        Files.writeString(missingNamespace, """
                catalog: data_lake
                """);
        assertThatThrownBy(() -> fileModelManager.parseNamespace(missingNamespace))
                .isInstanceOf(InvalidNamespaceModelException.class);

        Path badIdentifier = tempDir.resolve("bad_id.yaml");
        Files.writeString(badIdentifier, """
                catalog: data_lake
                namespace: market.data
                """);
        assertThatThrownBy(() -> fileModelManager.parseNamespace(badIdentifier))
                .isInstanceOf(InvalidNamespaceModelException.class);

        Path unknownKey = tempDir.resolve("unknown_key.yaml");
        Files.writeString(unknownKey, """
                catalog: data_lake
                namespace: market_data
                extraField: unexpected
                """);
        assertThatThrownBy(() -> fileModelManager.parseNamespace(unknownKey))
                .isInstanceOf(ModelParseException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void tc016SupportsConcurrentParsingAcrossMultipleThreads() throws Exception {
        Path fixture = resourcePath("/table.yaml");
        TableModel expected = fileModelManager.parseTable(fixture);

        int threads = 8;
        int invocations = 40;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<TableModel>> tasks = new ArrayList<>(invocations);
            for (int i = 0; i < invocations; i++) {
                tasks.add(() -> fileModelManager.parseTable(fixture));
            }
            List<Future<TableModel>> futures = executor.invokeAll(tasks);
            for (Future<TableModel> future : futures) {
                assertThat(future.get()).isEqualTo(expected);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Path resourcePath(String resource) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                YamlFileModelManagerTest.class.getResource(resource),
                "Missing test resource: " + resource
        ).toURI());
    }
}
