package x.ruiz.playground.data.engineering.lakehouse.core.exception;

import x.ruiz.playground.data.engineering.lakehouse.core.spec.NamespaceSpec;

/**
 * Thrown when a table operation targets a namespace that does not exist within an existing catalog.
 *
 * <p>Engine implementations must raise this exception rather than silently auto-creating missing namespaces.
 */
public class NamespaceNotFoundException extends LakehouseException {

    /** Specification of the missing namespace. */
    private final NamespaceSpec namespaceSpec;

    /**
     * Creates a new exception for the missing namespace.
     *
     * @param namespace non-null specification of the missing namespace
     * @throws InvalidNamespaceSpecException if {@code namespace} is null
     */
    public NamespaceNotFoundException(NamespaceSpec namespace) {
        super(formatMessage(namespace));
        this.namespaceSpec = namespace;
    }

    /**
     * Creates a new exception for the missing namespace, preserving the underlying engine cause.
     *
     * @param namespace non-null specification of the missing namespace
     * @param cause     underlying engine or client exception
     * @throws InvalidNamespaceSpecException if {@code namespace} is null
     */
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

    /**
     * Returns the {@link NamespaceSpec} that could not be found.
     *
     * @return the missing namespace specification
     */
    public NamespaceSpec getNamespaceSpec() {
        return namespaceSpec;
    }

    /**
     * Returns the fully qualified name ({@code <catalog>.<namespace>}) of the missing namespace.
     *
     * @return namespace fully qualified name
     */
    public String getFullyQualifiedName() {
        return namespaceSpec.fullyQualifiedName();
    }
}
