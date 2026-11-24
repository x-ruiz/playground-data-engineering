package x.ruiz.playground.data.engineering.lakehouse.core.exception;

import x.ruiz.playground.data.engineering.lakehouse.core.spec.CatalogSpec;

/**
 * Thrown when a manager operation targets a catalog that has not been provisioned by the platform.
 *
 * <p>Engine implementations must raise this exception rather than auto-creating missing catalogs.
 */
public class CatalogNotFoundException extends LakehouseException {

    /** Specification of the missing catalog. */
    private final CatalogSpec catalogSpec;

    /**
     * Creates a new exception for the missing catalog.
     *
     * @param catalog non-null specification of the missing catalog
     * @throws InvalidCatalogSpecException if {@code catalog} is null
     */
    public CatalogNotFoundException(CatalogSpec catalog) {
        super(formatMessage(catalog));
        this.catalogSpec = catalog;
    }

    /**
     * Creates a new exception for the missing catalog, preserving the underlying engine cause.
     *
     * @param catalog non-null specification of the missing catalog
     * @param cause   underlying engine or client exception
     * @throws InvalidCatalogSpecException if {@code catalog} is null
     */
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

    /**
     * Returns the {@link CatalogSpec} that could not be found.
     *
     * @return the missing catalog specification
     */
    public CatalogSpec getCatalogSpec() {
        return catalogSpec;
    }

    /**
     * Returns the fully qualified name of the missing catalog.
     *
     * @return catalog fully qualified name
     */
    public String getFullyQualifiedName() {
        return catalogSpec.fullyQualifiedName();
    }
}
