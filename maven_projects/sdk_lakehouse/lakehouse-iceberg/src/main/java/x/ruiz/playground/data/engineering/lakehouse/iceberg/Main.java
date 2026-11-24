package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import x.ruiz.playground.data.engineering.lakehouse.core.spec.CatalogSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.NamespaceSpec;

public class Main {
    public static void main(String[] args) {
        CatalogSpec catalogSpec = new CatalogSpec("data_lake");
        NamespaceSpec namespaceSpec = new NamespaceSpec(catalogSpec, "market_data");
        System.out.println(namespaceSpec.fullyQualifiedName());
    }
}
