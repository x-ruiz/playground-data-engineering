package x.ruiz.playground.data.engineering.lakehouse.core;

import java.nio.file.Path;

public interface FileParser<T extends LakehouseSpec> {

    T parseData(Path sourcePath);
}
