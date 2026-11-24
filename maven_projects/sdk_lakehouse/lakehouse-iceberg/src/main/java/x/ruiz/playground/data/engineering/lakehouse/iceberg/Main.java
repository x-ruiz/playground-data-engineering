package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import x.ruiz.playground.data.engineering.lakehouse.core.spec.CatalogSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.NamespaceSpec;

public class Main {
    public static void main(String[] args) {
        CatalogSpec catalogSpec = new CatalogSpec("data_lakehouse");
        NamespaceSpec namespaceSpec = new NamespaceSpec(catalogSpec, "marketdata");
        System.out.println(namespaceSpec.fullyQualifiedName());
    }
}
