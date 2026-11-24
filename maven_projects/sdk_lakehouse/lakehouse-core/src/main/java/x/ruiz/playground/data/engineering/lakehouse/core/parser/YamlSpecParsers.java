package x.ruiz.playground.data.engineering.lakehouse.core.parser;

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

public final class YamlSpecParsers {

    private static final Logger LOGGER = LogManager.getLogger(YamlSpecParsers.class);

    private static final YAMLMapper STRICT_MAPPER = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private YamlSpecParsers() {
    }

    public static TableSpec parseTable(Path sourcePath) {
        YamlTableDto dto = readYaml(sourcePath, YamlTableDto.class, "table");
        return toTableSpec(dto);
    }

    public static NamespaceSpec parseNamespace(Path sourcePath) {
        YamlNamespaceDto dto = readYaml(sourcePath, YamlNamespaceDto.class, "namespace");
        CatalogSpec catalogSpec = new CatalogSpec(dto.catalog());
        return new NamespaceSpec(catalogSpec, dto.namespace());
    }

    private static <D> D readYaml(Path sourcePath, Class<D> dtoType, String specKind) {
        if (sourcePath == null) {
            throw new SpecParseException("Source path must not be null");
        }
        LOGGER.info("Parsing {} specification from path: {}", specKind, sourcePath);

        D dto;
        try (InputStream inputStream = Files.newInputStream(sourcePath)) {
            dto = STRICT_MAPPER.readValue(inputStream, dtoType);
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
