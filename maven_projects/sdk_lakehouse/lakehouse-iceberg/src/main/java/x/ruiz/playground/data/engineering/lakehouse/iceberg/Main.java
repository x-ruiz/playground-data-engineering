package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.NamespaceModel;

/**
 * Minimal CLI entry point placeholder for local smoke runs.
 */
public final class Main {

    private Main() {
    }

    /**
     * Runs a minimal identity smoke print.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        CatalogModel catalogModel = new CatalogModel("data_lake");
        NamespaceModel namespaceModel = new NamespaceModel(catalogModel, "market_data");
        System.out.println(namespaceModel.fullyQualifiedName());
    }
}
