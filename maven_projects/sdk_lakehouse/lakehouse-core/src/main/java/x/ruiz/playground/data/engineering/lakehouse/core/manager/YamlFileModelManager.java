package x.ruiz.playground.data.engineering.lakehouse.core.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.ModelParseException;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.ColumnModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.ColumnType;
import x.ruiz.playground.data.engineering.lakehouse.core.model.NamespaceModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.TableModel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Package-private, stateless, thread-safe YAML implementation of {@link FileModelManager}.
 *
 * <p>Uses a strict {@link YAMLMapper} (failing fast on unknown properties) and private DTO records
 * so Jackson dependencies and YAML key names never leak into {@code ...core.model} domain records.
 */
final class YamlFileModelManager implements FileModelManager {

    /** Shared stateless singleton instance used by default in {@link TableManager} and {@link NamespaceManager}. */
    static final YamlFileModelManager INSTANCE = new YamlFileModelManager();

    private static final Logger LOGGER = LogManager.getLogger(YamlFileModelManager.class);

    private static final YAMLMapper STRICT_MAPPER = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    YamlFileModelManager() {
    }

    /**
     * Parses a YAML table specification file into a validated {@link TableModel}.
     *
     * <p>Columns omitting {@code nullable} default to {@code true}.
     *
     * @param sourcePath path to the table YAML file
     * @return validated {@link TableModel}
     * @throws ModelParseException if the file is missing, unreadable, empty, malformed, or has unknown keys
     */
    @Override
    public TableModel parseTable(Path sourcePath) {
        YamlTableDto dto = readFile(sourcePath, YamlTableDto.class, "table");
        return toTableModel(dto);
    }

    /**
     * Parses a YAML namespace specification file into a validated {@link NamespaceModel}.
     *
     * @param sourcePath path to the namespace YAML file
     * @return validated {@link NamespaceModel}
     * @throws ModelParseException if the file is missing, unreadable, empty, malformed, or has unknown keys
     */
    @Override
    public NamespaceModel parseNamespace(Path sourcePath) {
        YamlNamespaceDto dto = readFile(sourcePath, YamlNamespaceDto.class, "namespace");
        CatalogModel catalogModel = new CatalogModel(dto.catalog());
        return new NamespaceModel(catalogModel, dto.namespace());
    }

    /**
     * Reads and deserializes a YAML file into the given DTO type using {@link #STRICT_MAPPER}.
     *
     * @param sourcePath path to the YAML file
     * @param targetType DTO class to deserialize into
     * @param modelKind   human-readable spec category for logging and error messages
     * @param <D>        DTO type
     * @return non-null deserialized DTO
     * @throws ModelParseException if {@code sourcePath} is null, unreadable, empty, malformed, or has unknown keys
     */
    @Override
    public <D> D readFile(Path sourcePath, Class<D> targetType, String modelKind) {
        if (sourcePath == null) {
            throw new ModelParseException("Source path must not be null");
        }
        LOGGER.info("Parsing {} specification from path: {}", modelKind, sourcePath);

        D dto;
        try (InputStream inputStream = Files.newInputStream(sourcePath)) {
            dto = STRICT_MAPPER.readValue(inputStream, targetType);
        } catch (JsonProcessingException e) {
            throw new ModelParseException("Invalid YAML " + modelKind + " specification in " + sourcePath, e);
        } catch (IOException e) {
            throw new ModelParseException("Failed to read " + modelKind + " specification file: " + sourcePath, e);
        }

        if (dto == null) {
            throw new ModelParseException("YAML " + modelKind + " specification file is empty: " + sourcePath);
        }
        return dto;
    }

    private static TableModel toTableModel(YamlTableDto dto) {
        CatalogModel catalogModel = new CatalogModel(dto.catalog());
        NamespaceModel namespaceModel = new NamespaceModel(catalogModel, dto.namespace());

        List<ColumnModel> columns = null;
        if (dto.columns() != null) {
            columns = new ArrayList<>(dto.columns().size());
            for (YamlColumnDto columnDto : dto.columns()) {
                if (columnDto == null) {
                    // Preserve null entry so TableModel compact constructor enforces rejection uniformly.
                    columns.add(null);
                } else {
                    boolean nullable = columnDto.nullable() == null || columnDto.nullable();
                    ColumnType columnType = ColumnType.fromString(columnDto.type());
                    columns.add(new ColumnModel(columnDto.name(), columnType, nullable));
                }
            }
        }

        return new TableModel(namespaceModel, dto.tableName(), columns, dto.partitionColumn());
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
