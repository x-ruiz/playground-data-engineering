package x.ruiz.playground.data.engineering.lakehouse.core.exception;

import x.ruiz.playground.data.engineering.lakehouse.core.model.NamespaceModel;

/**
 * Thrown when a table operation targets a namespace that does not exist within an existing catalog.
 *
 * <p>Engine implementations must raise this exception rather than silently auto-creating missing namespaces.
 */
public class NamespaceNotFoundException extends LakehouseException {

    /** Specification of the missing namespace. */
    private final NamespaceModel namespaceModel;

    /**
     * Creates a new exception for the missing namespace.
     *
     * @param namespace non-null specification of the missing namespace
     * @throws InvalidNamespaceModelException if {@code namespace} is null
     */
    public NamespaceNotFoundException(NamespaceModel namespace) {
        super(formatMessage(namespace));
        this.namespaceModel = namespace;
    }

    /**
     * Creates a new exception for the missing namespace, preserving the underlying engine cause.
     *
     * @param namespace non-null specification of the missing namespace
     * @param cause     underlying engine or client exception
     * @throws InvalidNamespaceModelException if {@code namespace} is null
     */
    public NamespaceNotFoundException(NamespaceModel namespace, Throwable cause) {
        super(formatMessage(namespace), cause);
        this.namespaceModel = namespace;
    }

    private static String formatMessage(NamespaceModel namespace) {
        if (namespace == null) {
            throw new InvalidNamespaceModelException("Namespace model must not be null");
        }
        return "Namespace not found: " + namespace.fullyQualifiedName();
    }

    /**
     * Returns the {@link NamespaceModel} that could not be found.
     *
     * @return the missing namespace specification
     */
    public NamespaceModel getNamespaceModel() {
        return namespaceModel;
    }

    /**
     * Returns the fully qualified name ({@code <catalog>.<namespace>}) of the missing namespace.
     *
     * @return namespace fully qualified name
     */
    public String getFullyQualifiedName() {
        return namespaceModel.fullyQualifiedName();
    }
}
