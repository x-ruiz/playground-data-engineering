package x.ruiz.playground.data.engineering.lakehouse.core.exception;

import x.ruiz.playground.data.engineering.lakehouse.core.spec.CatalogSpec;

public class CatalogNotFoundException extends LakehouseException {

    private final CatalogSpec catalogSpec;

    public CatalogNotFoundException(CatalogSpec catalog) {
        super(formatMessage(catalog));
        this.catalogSpec = catalog;
    }

    public CatalogNotFoundException(CatalogSpec catalog, Throwable cause) {
        super(formatMessage(catalog), cause);
        this.catalogSpec = catalog;
    }

    private static String formatMessage(CatalogSpec catalog) {
        if (catalog == null) {
            throw new InvalidCatalogSpecException("Catalog spec must not be null");
        }
        return "Catalog not found: " + catalog.fullyQualifiedName();
    }

    public CatalogSpec getCatalogSpec() {
        return catalogSpec;
    }

    public String getFullyQualifiedName() {
        return catalogSpec.fullyQualifiedName();
    }
}
