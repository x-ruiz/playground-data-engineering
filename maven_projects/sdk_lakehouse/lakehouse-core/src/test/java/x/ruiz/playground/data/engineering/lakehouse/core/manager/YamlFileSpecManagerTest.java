package x.ruiz.playground.data.engineering.lakehouse.core.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidNamespaceSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.SpecParseException;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.CatalogSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.ColumnSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.ColumnType;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.NamespaceSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.TableSpec;

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

class YamlFileSpecManagerTest {

    private final FileSpecManager fileSpecManager = YamlFileSpecManager.INSTANCE;

    @Test
    void tc001ParsesValidTableYamlFixture() throws URISyntaxException {
        Path fixture = resourcePath("/table.yaml");

        TableSpec spec = fileSpecManager.parseTable(fixture);

        assertThat(spec.fullyQualifiedName()).isEqualTo("data_lake.market_data.transactions");
        assertThat(spec.catalog()).isEqualTo(new CatalogSpec("data_lake"));
        assertThat(spec.catalog().fullyQualifiedName()).isEqualTo("data_lake");
        assertThat(spec.namespace()).isEqualTo(new NamespaceSpec(new CatalogSpec("data_lake"), "market_data"));
        assertThat(spec.namespace().fullyQualifiedName()).isEqualTo("data_lake.market_data");
        assertThat(spec.name()).isEqualTo("transactions");
        assertThat(spec.isPartitioned()).isTrue();
        assertThat(spec.partitionColumn()).isEqualTo("date");
        assertThat(spec.partitionColumnOptional()).contains("date");
        assertThat(spec.columns()).containsExactly(
                new ColumnSpec("date", ColumnType.DATE, false),
                new ColumnSpec("id", ColumnType.BIGINT, false),
                new ColumnSpec("amount", ColumnType.DECIMAL, true)
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

        TableSpec spec = fileSpecManager.parseTable(yaml);

        assertThat(spec.isPartitioned()).isFalse();
        assertThat(spec.partitionColumnOptional()).isEmpty();
        assertThat(spec.columns()).containsExactly(
                new ColumnSpec("symbol", ColumnType.STRING, true),
                new ColumnSpec("price", ColumnType.DOUBLE, false)
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

        TableSpec spec = fileSpecManager.parseTable(yaml);

        assertThat(spec.columns()).containsExactly(
                new ColumnSpec("event_date", ColumnType.DATE, true),
                new ColumnSpec("event_id", ColumnType.BIGINT, true)
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

        assertThatThrownBy(() -> fileSpecManager.parseTable(yaml))
                .isInstanceOf(InvalidTableSpecException.class)
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
        assertThatThrownBy(() -> fileSpecManager.parseTable(badCatalog))
                .isInstanceOf(InvalidCatalogSpecException.class);

        Path badNamespace = tempDir.resolve("bad_namespace.yaml");
        Files.writeString(badNamespace, """
                catalog: data_lake
                namespace: bad namespace
                tableName: transactions
                columns:
                  - name: id
                    type: BIGINT
                """);
        assertThatThrownBy(() -> fileSpecManager.parseTable(badNamespace))
                .isInstanceOf(InvalidNamespaceSpecException.class);
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
        assertThatThrownBy(() -> fileSpecManager.parseTable(nullColumnEntry))
                .isInstanceOf(InvalidTableSpecException.class);

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
        assertThatThrownBy(() -> fileSpecManager.parseTable(badPartition))
                .isInstanceOf(InvalidTableSpecException.class)
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

        assertThatThrownBy(() -> fileSpecManager.parseTable(typoYaml))
                .isInstanceOf(SpecParseException.class)
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

        assertThatThrownBy(() -> fileSpecManager.parseTable(null)).isInstanceOf(SpecParseException.class);
        assertThatThrownBy(() -> fileSpecManager.parseTable(missing))
                .isInstanceOf(SpecParseException.class)
                .hasCauseInstanceOf(IOException.class);
        assertThatThrownBy(() -> fileSpecManager.parseTable(empty)).isInstanceOf(SpecParseException.class);
        assertThatThrownBy(() -> fileSpecManager.parseTable(commentOnly)).isInstanceOf(SpecParseException.class);
        assertThatThrownBy(() -> fileSpecManager.parseTable(malformed))
                .isInstanceOf(SpecParseException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);

        assertThatThrownBy(() -> fileSpecManager.parseNamespace(null)).isInstanceOf(SpecParseException.class);
        assertThatThrownBy(() -> fileSpecManager.parseNamespace(missing))
                .isInstanceOf(SpecParseException.class)
                .hasCauseInstanceOf(IOException.class);
        assertThatThrownBy(() -> fileSpecManager.parseNamespace(empty)).isInstanceOf(SpecParseException.class);
        assertThatThrownBy(() -> fileSpecManager.parseNamespace(commentOnly)).isInstanceOf(SpecParseException.class);
        assertThatThrownBy(() -> fileSpecManager.parseNamespace(malformed))
                .isInstanceOf(SpecParseException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void tc011ParsesValidNamespaceYamlFixture() throws URISyntaxException {
        Path fixture = resourcePath("/namespace.yaml");

        NamespaceSpec spec = fileSpecManager.parseNamespace(fixture);

        assertThat(spec.catalog()).isEqualTo(new CatalogSpec("data_lake"));
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
        assertThatThrownBy(() -> fileSpecManager.parseNamespace(missingCatalog))
                .isInstanceOf(InvalidCatalogSpecException.class);

        Path missingNamespace = tempDir.resolve("missing_ns.yaml");
        Files.writeString(missingNamespace, """
                catalog: data_lake
                """);
        assertThatThrownBy(() -> fileSpecManager.parseNamespace(missingNamespace))
                .isInstanceOf(InvalidNamespaceSpecException.class);

        Path badIdentifier = tempDir.resolve("bad_id.yaml");
        Files.writeString(badIdentifier, """
                catalog: data_lake
                namespace: market.data
                """);
        assertThatThrownBy(() -> fileSpecManager.parseNamespace(badIdentifier))
                .isInstanceOf(InvalidNamespaceSpecException.class);

        Path unknownKey = tempDir.resolve("unknown_key.yaml");
        Files.writeString(unknownKey, """
                catalog: data_lake
                namespace: market_data
                extraField: unexpected
                """);
        assertThatThrownBy(() -> fileSpecManager.parseNamespace(unknownKey))
                .isInstanceOf(SpecParseException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void tc016SupportsConcurrentParsingAcrossMultipleThreads() throws Exception {
        Path fixture = resourcePath("/table.yaml");
        TableSpec expected = fileSpecManager.parseTable(fixture);

        int threads = 8;
        int invocations = 40;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<TableSpec>> tasks = new ArrayList<>(invocations);
            for (int i = 0; i < invocations; i++) {
                tasks.add(() -> fileSpecManager.parseTable(fixture));
            }
            List<Future<TableSpec>> futures = executor.invokeAll(tasks);
            for (Future<TableSpec> future : futures) {
                assertThat(future.get()).isEqualTo(expected);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Path resourcePath(String resource) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                YamlFileSpecManagerTest.class.getResource(resource),
                "Missing test resource: " + resource
        ).toURI());
    }
}
