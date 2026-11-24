package x.ruiz.playground.data.engineering.lakehouse.core.spec;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogSpecException;

/**
 * Immutable identity reference to a platform-provisioned lakehouse catalog.
 *
 * <p>Catalogs are provisioned externally by platform infrastructure rather than declarative YAML;
 * connection and engine configuration belong in engine adapter modules.
 *
 * @param name validated catalog identifier matching {@link LakehouseSpec#VALID_IDENTIFIER}
 */
public record CatalogSpec(String name) implements LakehouseSpec {

    /**
     * Validates the catalog identifier upon construction.
     *
     * @throws InvalidCatalogSpecException if {@code name} is null, blank, or invalid
     */
    public CatalogSpec {
        LakehouseSpec.validateIdentifier("catalog", name, InvalidCatalogSpecException::new);
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
