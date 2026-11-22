package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import x.ruiz.playground.data.engineering.lakehouse.core.TableParser;
import x.ruiz.playground.data.engineering.lakehouse.core.YamlTableParser;

public class Main {
    public static void main(String[] args) {
        TableParser parser = new YamlTableParser();
        System.out.println(parser.parseData());
    }
}
