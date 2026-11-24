package x.ruiz.playground.data.engineering.lakehouse.core.spec;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidNamespaceSpecException;

/**
 * Immutable specification identifying a namespace within a {@link CatalogSpec}.
 *
 * @param catalog non-null owning catalog reference
 * @param name    validated namespace identifier matching {@link LakehouseSpec#VALID_IDENTIFIER}
 */
public record NamespaceSpec(
        CatalogSpec catalog,
        String name
) implements LakehouseSpec {

    /**
     * Validates that {@code catalog} is non-null and {@code name} matches {@link LakehouseSpec#VALID_IDENTIFIER}.
     *
     * @throws InvalidNamespaceSpecException if {@code catalog} is null or {@code name} is invalid
     */
    public NamespaceSpec {
        if (catalog == null) {
            throw new InvalidNamespaceSpecException("Catalog spec must not be null");
        }
        LakehouseSpec.validateIdentifier("namespace", name, InvalidNamespaceSpecException::new);
    }

    /**
     * Returns the fully qualified namespace identifier formatted as {@code <catalog>.<namespace>}.
     *
     * @return dot-delimited catalog and namespace name
     */
    @Override
    public String fullyQualifiedName() {
        return catalog.fullyQualifiedName() + "." + name;
    }
}
