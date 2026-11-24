package x.ruiz.playground.data.engineering.lakehouse.core;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidNamespaceSpecException;

public record NamespaceSpec(
        CatalogSpec catalog,
        String name
) implements LakehouseSpec {

    public NamespaceSpec {
        if (catalog == null) {
            throw new InvalidNamespaceSpecException("Catalog spec must not be null");
        }
        LakehouseSpec.validateIdentifier("namespace", name, InvalidNamespaceSpecException::new);
    }

    @Override
    public String fullyQualifiedName() {
        return catalog.fullyQualifiedName() + "." + name;
    }
}
