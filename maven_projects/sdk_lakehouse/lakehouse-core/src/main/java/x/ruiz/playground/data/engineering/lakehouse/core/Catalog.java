package x.ruiz.playground.data.engineering.lakehouse.core;

import java.util.Collections;
import java.util.Map;

public interface Catalog {
    String name();
    String catalogConfigPrefix();

    default Map<String, String> properties() {
        return Collections.emptyMap();
    }
}
