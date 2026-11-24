package x.ruiz.playground.data.engineering.lakehouse.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

final class YamlMappers {

    static final YAMLMapper STRICT = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private YamlMappers() {
    }
}
