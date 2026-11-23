package x.ruiz.playground.data.engineering.lakehouse.core;

public interface FileTableParser {
    public TableSpec parseData(String sourcePath);
}
