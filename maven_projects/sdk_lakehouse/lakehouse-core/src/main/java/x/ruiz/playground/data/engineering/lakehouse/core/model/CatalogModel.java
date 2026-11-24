package x.ruiz.playground.data.engineering.lakehouse.core.model;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException;

/**
 * Immutable identity reference to a platform-provisioned lakehouse catalog.
 *
 * <p>Catalogs are provisioned externally by platform infrastructure rather than declarative YAML;
 * connection and engine configuration belong in engine adapter modules.
 *
 * @param name validated catalog identifier matching {@link LakehouseModel#VALID_IDENTIFIER}
 */
public record CatalogModel(String name) implements LakehouseModel {

    /**
     * Validates the catalog identifier upon construction.
     *
     * @throws InvalidCatalogModelException if {@code name} is null, blank, or invalid
     */
    public CatalogModel {
        LakehouseModel.validateIdentifier("catalog", name, InvalidCatalogModelException::new);
    }

    /**
     * Returns the catalog name as its fully qualified identifier.
     *
     * @return the catalog name
     */
    @Override
    public String fullyQualifiedName() {
        return name;
    }
}
