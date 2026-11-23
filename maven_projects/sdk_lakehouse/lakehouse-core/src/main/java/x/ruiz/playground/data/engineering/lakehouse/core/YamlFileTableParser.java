package x.ruiz.playground.data.engineering.lakehouse.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

public class YamlFileTableParser implements FileTableParser {
    private static final Logger logger = LogManager.getLogger(YamlFileTableParser.class);
    YAMLMapper mapper = new YAMLMapper();

    public TableSpec parseData(String path) {
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