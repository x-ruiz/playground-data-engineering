package x.ruiz.playground.data.engineering.lakehouse.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.SpecParseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class YamlFileNamespaceParser implements FileParser<NamespaceSpec> {

    private static final Logger LOGGER = LogManager.getLogger(YamlFileNamespaceParser.class);

    public YamlFileNamespaceParser() {
    }

    @Override
    public NamespaceSpec parseData(Path sourcePath) {
        if (sourcePath == null) {
            throw new SpecParseException("Source path must not be null");
        }
        LOGGER.info("Parsing namespace specification from path: {}", sourcePath);

        YamlNamespaceDto dto;
        try (InputStream inputStream = Files.newInputStream(sourcePath)) {
            dto = YamlMappers.STRICT.readValue(inputStream, YamlNamespaceDto.class);
        } catch (JsonProcessingException e) {
            throw new SpecParseException("Invalid YAML namespace specification in " + sourcePath, e);
        } catch (IOException e) {
            throw new SpecParseException("Failed to read namespace specification file: " + sourcePath, e);
        }

        if (dto == null) {
            throw new SpecParseException("Namespace specification YAML file is empty: " + sourcePath);
        }

        CatalogSpec catalogSpec = new CatalogSpec(dto.catalog());
        return new NamespaceSpec(catalogSpec, dto.namespace());
    }

    private record YamlNamespaceDto(
            String catalog,
            String namespace
    ) {}
}
