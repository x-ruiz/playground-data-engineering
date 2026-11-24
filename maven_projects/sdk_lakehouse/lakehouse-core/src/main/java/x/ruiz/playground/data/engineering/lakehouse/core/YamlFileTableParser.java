package x.ruiz.playground.data.engineering.lakehouse.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.SpecParseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class YamlFileTableParser implements FileParser<TableSpec> {

    private static final Logger LOGGER = LogManager.getLogger(YamlFileTableParser.class);

    public YamlFileTableParser() {
    }

    @Override
    public TableSpec parseData(Path sourcePath) {
        if (sourcePath == null) {
            throw new SpecParseException("Source path must not be null");
        }
        LOGGER.info("Parsing table specification from path: {}", sourcePath);

        YamlTableDto dto;
        try (InputStream inputStream = Files.newInputStream(sourcePath)) {
            dto = YamlMappers.STRICT.readValue(inputStream, YamlTableDto.class);
        } catch (JsonProcessingException e) {
            throw new SpecParseException("Invalid YAML table specification in " + sourcePath, e);
        } catch (IOException e) {
            throw new SpecParseException("Failed to read table specification file: " + sourcePath, e);
        }

        if (dto == null) {
            throw new SpecParseException("Table specification YAML file is empty: " + sourcePath);
        }

        return toTableSpec(dto);
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
}
