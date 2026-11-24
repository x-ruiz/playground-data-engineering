package x.ruiz.playground.data.engineering.lakehouse.core.exception;

import x.ruiz.playground.data.engineering.lakehouse.core.NamespaceSpec;

public class NamespaceNotFoundException extends LakehouseException {

    private final NamespaceSpec namespaceSpec;

    public NamespaceNotFoundException(NamespaceSpec namespace) {
        super(formatMessage(namespace));
        this.namespaceSpec = namespace;
    }

    public NamespaceNotFoundException(NamespaceSpec namespace, Throwable cause) {
        super(formatMessage(namespace), cause);
        this.namespaceSpec = namespace;
    }

    private static String formatMessage(NamespaceSpec namespace) {
        if (namespace == null) {
            throw new InvalidNamespaceSpecException("Namespace spec must not be null");
        }
        return "Namespace not found: " + namespace.fullyQualifiedName();
    }

    public NamespaceSpec getNamespaceSpec() {
        return namespaceSpec;
    }

    public String getFullyQualifiedName() {
        return namespaceSpec.fullyQualifiedName();
    }
}
