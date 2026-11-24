package x.ruiz.playground.data.engineering.lakehouse.core.exception;

import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;

/**
 * Thrown when a manager operation targets a catalog that has not been provisioned by the platform.
 *
 * <p>Engine implementations must raise this exception rather than auto-creating missing catalogs.
 */
public class CatalogNotFoundException extends LakehouseException {

    /** Specification of the missing catalog. */
    private final CatalogModel catalogModel;

    /**
     * Creates a new exception for the missing catalog.
     *
     * @param catalog non-null specification of the missing catalog
     * @throws InvalidCatalogModelException if {@code catalog} is null
     */
    public CatalogNotFoundException(CatalogModel catalog) {
        super(formatMessage(catalog));
        this.catalogModel = catalog;
    }

    /**
     * Creates a new exception for the missing catalog, preserving the underlying engine cause.
     *
     * @param catalog non-null specification of the missing catalog
     * @param cause   underlying engine or client exception
     * @throws InvalidCatalogModelException if {@code catalog} is null
     */
    public CatalogNotFoundException(CatalogModel catalog, Throwable cause) {
        super(formatMessage(catalog), cause);
        this.catalogModel = catalog;
    }

    private static String formatMessage(CatalogModel catalog) {
        if (catalog == null) {
            throw new InvalidCatalogModelException("Catalog model must not be null");
        }
        return "Catalog not found: " + catalog.fullyQualifiedName();
    }

    /**
     * Returns the {@link CatalogModel} that could not be found.
     *
     * @return the missing catalog specification
     */
    public CatalogModel getCatalogModel() {
        return catalogModel;
    }

    /**
     * Returns the fully qualified name of the missing catalog.
     *
     * @return catalog fully qualified name
     */
    public String getFullyQualifiedName() {
        return catalogModel.fullyQualifiedName();
    }
}
