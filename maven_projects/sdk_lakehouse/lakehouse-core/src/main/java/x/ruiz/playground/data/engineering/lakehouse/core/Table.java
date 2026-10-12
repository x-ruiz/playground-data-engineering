package x.ruiz.playground.data.engineering.lakehouse.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface Table {
    String name();
    String namespace();
    String schema();

    default Map<String, String> properties() {
        return Collections.emptyMap();
    }

    default List<String> partitionColumns() {
        return Collections.emptyList();
    }

    default List<String> sortColumns() {
        return Collections.emptyList();
    }

    default String location() {
        return null;
    }
}
