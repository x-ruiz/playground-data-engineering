package x.ruiz.playground.data.engineering.lakehouse.core.model;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidNamespaceModelException;

/**
 * Immutable specification identifying a namespace within a {@link CatalogModel}.
 *
 * @param catalog non-null owning catalog reference
 * @param name    validated namespace identifier matching {@link LakehouseModel#VALID_IDENTIFIER}
 */
public record NamespaceModel(
        CatalogModel catalog,
        String name
) implements LakehouseModel {

    /**
     * Validates that {@code catalog} is non-null and {@code name} matches {@link LakehouseModel#VALID_IDENTIFIER}.
     *
     * @throws InvalidNamespaceModelException if {@code catalog} is null or {@code name} is invalid
     */
    public NamespaceModel {
        if (catalog == null) {
            throw new InvalidNamespaceModelException("Catalog model must not be null");
        }
        LakehouseModel.validateIdentifier("namespace", name, InvalidNamespaceModelException::new);
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
