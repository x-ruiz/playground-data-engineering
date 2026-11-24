package x.ruiz.playground.data.engineering.lakehouse.core.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.SpecParseException;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.CatalogSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.ColumnSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.ColumnType;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.NamespaceSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.TableSpec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Package-private, stateless, thread-safe YAML implementation of {@link FileSpecManager}.
 *
 * <p>Uses a strict {@link YAMLMapper} (failing fast on unknown properties) and private DTO records
 * so Jackson dependencies and YAML key names never leak into {@code ...core.spec} domain records.
 */
final class YamlFileSpecManager implements FileSpecManager {

    /** Shared stateless singleton instance used by default in {@link TableManager} and {@link NamespaceManager}. */
    static final YamlFileSpecManager INSTANCE = new YamlFileSpecManager();

    private static final Logger LOGGER = LogManager.getLogger(YamlFileSpecManager.class);

    private static final YAMLMapper STRICT_MAPPER = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    YamlFileSpecManager() {
    }

    /**
     * Parses a YAML table specification file into a validated {@link TableSpec}.
     *
     * <p>Columns omitting {@code nullable} default to {@code true}.
     *
     * @param sourcePath path to the table YAML file
     * @return validated {@link TableSpec}
     * @throws SpecParseException if the file is missing, unreadable, empty, malformed, or has unknown keys
     */
    @Override
    public TableSpec parseTable(Path sourcePath) {
        YamlTableDto dto = readFile(sourcePath, YamlTableDto.class, "table");
        return toTableSpec(dto);
    }

    /**
     * Parses a YAML namespace specification file into a validated {@link NamespaceSpec}.
     *
     * @param sourcePath path to the namespace YAML file
     * @return validated {@link NamespaceSpec}
     * @throws SpecParseException if the file is missing, unreadable, empty, malformed, or has unknown keys
     */
    @Override
    public NamespaceSpec parseNamespace(Path sourcePath) {
        YamlNamespaceDto dto = readFile(sourcePath, YamlNamespaceDto.class, "namespace");
        CatalogSpec catalogSpec = new CatalogSpec(dto.catalog());
        return new NamespaceSpec(catalogSpec, dto.namespace());
    }

    /**
     * Reads and deserializes a YAML file into the given DTO type using {@link #STRICT_MAPPER}.
     *
     * @param sourcePath path to the YAML file
     * @param targetType DTO class to deserialize into
     * @param specKind   human-readable spec category for logging and error messages
     * @param <D>        DTO type
     * @return non-null deserialized DTO
     * @throws SpecParseException if {@code sourcePath} is null, unreadable, empty, malformed, or has unknown keys
     */
    @Override
    public <D> D readFile(Path sourcePath, Class<D> targetType, String specKind) {
        if (sourcePath == null) {
            throw new SpecParseException("Source path must not be null");
        }
        LOGGER.info("Parsing {} specification from path: {}", specKind, sourcePath);

        D dto;
        try (InputStream inputStream = Files.newInputStream(sourcePath)) {
            dto = STRICT_MAPPER.readValue(inputStream, targetType);
        } catch (JsonProcessingException e) {
            throw new SpecParseException("Invalid YAML " + specKind + " specification in " + sourcePath, e);
        } catch (IOException e) {
            throw new SpecParseException("Failed to read " + specKind + " specification file: " + sourcePath, e);
        }

        if (dto == null) {
            throw new SpecParseException("YAML " + specKind + " specification file is empty: " + sourcePath);
        }
        return dto;
    }

    private static TableSpec toTableSpec(YamlTableDto dto) {
        CatalogSpec catalogSpec = new CatalogSpec(dto.catalog());
        NamespaceSpec namespaceSpec = new NamespaceSpec(catalogSpec, dto.namespace());

        List<ColumnSpec> columns = null;
        if (dto.columns() != null) {
            columns = new ArrayList<>(dto.columns().size());
            for (YamlColumnDto columnDto : dto.columns()) {
                if (columnDto == null) {
                    // Preserve null entry so TableSpec compact constructor enforces rejection uniformly.
                    columns.add(null);
                } else {
                    boolean nullable = columnDto.nullable() == null || columnDto.nullable();
                    ColumnType columnType = ColumnType.fromString(columnDto.type());
                    columns.add(new ColumnSpec(columnDto.name(), columnType, nullable));
                }
            }
        }

        return new TableSpec(namespaceSpec, dto.tableName(), columns, dto.partitionColumn());
    }

    private record YamlTableDto(
            String catalog,
            String namespace,
            String tableName,
            List<YamlColumnDto> columns,
            String partitionColumn
    ) {}

    private record YamlColumnDto(
            String name,
            String type,
            Boolean nullable
    ) {}

    private record YamlNamespaceDto(
            String catalog,
            String namespace
    ) {}
}
