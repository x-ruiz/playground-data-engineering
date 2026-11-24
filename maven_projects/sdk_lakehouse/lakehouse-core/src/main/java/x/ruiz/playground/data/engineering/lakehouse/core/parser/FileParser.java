package x.ruiz.playground.data.engineering.lakehouse.core.parser;

import x.ruiz.playground.data.engineering.lakehouse.core.spec.LakehouseSpec;

import java.nio.file.Path;

@FunctionalInterface
public interface FileParser<T extends LakehouseSpec> {

    T parseData(Path sourcePath);
}
