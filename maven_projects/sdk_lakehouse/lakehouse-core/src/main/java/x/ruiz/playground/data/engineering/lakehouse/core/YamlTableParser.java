package x.ruiz.playground.data.engineering.lakehouse.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

public class YamlTableParser implements TableParser {
    private static final Logger logger = LogManager.getLogger(YamlTableParser.class);

    YAMLMapper mapper = new YAMLMapper();

    public TableSpec parseData() {
        String path = "/Users/xavierruiz/Library/Mobile Documents/com~apple~CloudDocs/Documents/repositories/x-ruiz/playground-data-engineering/maven_projects/sdk_lakehouse/lakehouse-core/src/test/resources/table.yaml";
        logger.info("Processing data for path: {}", path);
        try {
            return mapper.readValue(new File(path), TableSpec.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Invalid YAML Table Specification", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load file", e);
        }
    }
}