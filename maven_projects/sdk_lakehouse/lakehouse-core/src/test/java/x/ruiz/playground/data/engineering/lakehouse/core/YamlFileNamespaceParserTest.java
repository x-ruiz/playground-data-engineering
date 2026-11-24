package x.ruiz.playground.data.engineering.lakehouse.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidNamespaceSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.SpecParseException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlFileNamespaceParserTest {

    private final FileParser<NamespaceSpec> parser = new YamlFileNamespaceParser();

    @Test
    void tc011ParsesValidNamespaceYamlFixture() throws URISyntaxException {
        Path fixture = resourcePath("/namespace.yaml");

        NamespaceSpec spec = parser.parseData(fixture);

        assertThat(spec.catalog()).isEqualTo(new CatalogSpec("data_lake"));
        assertThat(spec.catalog().fullyQualifiedName()).isEqualTo("data_lake");
        assertThat(spec.name()).isEqualTo("market_data");
        assertThat(spec.fullyQualifiedName()).isEqualTo("data_lake.market_data");
    }

    @Test
    void tc013RejectsInvalidNamespaceYamlSemanticAndUnknownKeyErrors(@TempDir Path tempDir) throws IOException {
        Path missingCatalog = tempDir.resolve("missing_catalog.yaml");
        Files.writeString(missingCatalog, """
                namespace: market_data
                """);
        assertThatThrownBy(() -> parser.parseData(missingCatalog))
                .isInstanceOf(InvalidCatalogSpecException.class);

        Path missingNamespace = tempDir.resolve("missing_ns.yaml");
        Files.writeString(missingNamespace, """
                catalog: data_lake
                """);
        assertThatThrownBy(() -> parser.parseData(missingNamespace))
                .isInstanceOf(InvalidNamespaceSpecException.class);

        Path badIdentifier = tempDir.resolve("bad_id.yaml");
        Files.writeString(badIdentifier, """
                catalog: data_lake
                namespace: market.data
                """);
        assertThatThrownBy(() -> parser.parseData(badIdentifier))
                .isInstanceOf(InvalidNamespaceSpecException.class);

        Path unknownKey = tempDir.resolve("unknown_key.yaml");
        Files.writeString(unknownKey, """
                catalog: data_lake
                namespace: market_data
                extraField: unexpected
                """);
        assertThatThrownBy(() -> parser.parseData(unknownKey))
                .isInstanceOf(SpecParseException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void tc010RejectsNullMissingEmptyAndMalformedNamespaceYaml(@TempDir Path tempDir) throws IOException {
        assertThatThrownBy(() -> parser.parseData(null))
                .isInstanceOf(SpecParseException.class);

        Path missing = tempDir.resolve("missing.yaml");
        assertThatThrownBy(() -> parser.parseData(missing))
                .isInstanceOf(SpecParseException.class)
                .hasCauseInstanceOf(IOException.class);

        Path empty = tempDir.resolve("empty.yaml");
        Files.writeString(empty, "");
        assertThatThrownBy(() -> parser.parseData(empty))
                .isInstanceOf(SpecParseException.class);

        Path commentOnly = tempDir.resolve("comments.yaml");
        Files.writeString(commentOnly, "# comment only\n");
        assertThatThrownBy(() -> parser.parseData(commentOnly))
                .isInstanceOf(SpecParseException.class);

        Path malformed = tempDir.resolve("malformed.yaml");
        Files.writeString(malformed, "catalog: [broken\nnamespace: : :");
        assertThatThrownBy(() -> parser.parseData(malformed))
                .isInstanceOf(SpecParseException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    private static Path resourcePath(String resource) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                YamlFileNamespaceParserTest.class.getResource(resource),
                "Missing test resource: " + resource
        ).toURI());
    }
}
