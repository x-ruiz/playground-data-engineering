package x.ruiz.playground.data.engineering.lakehouse.core.spec;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogSpecException;

public record CatalogSpec(String name) implements LakehouseSpec {

    public CatalogSpec {
        LakehouseSpec.validateIdentifier("catalog", name, InvalidCatalogSpecException::new);
    }

    @Override
    public String fullyQualifiedName() {
        return name;
    }
}
