package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import x.ruiz.playground.data.engineering.lakehouse.core.FileTableParser;
import x.ruiz.playground.data.engineering.lakehouse.core.YamlFileTableParser;

public class Main {
    public static void main(String[] args) {
        String path = "/Users/xavierruiz/Library/Mobile Documents/com~apple~CloudDocs/Documents/repositories/x-ruiz/playground-data-engineering/maven_projects/sdk_lakehouse/lakehouse-core/src/test/resources/table.yaml";

        FileTableParser parser = new YamlFileTableParser();
        System.out.println(parser.parseData(path));
    }
}
