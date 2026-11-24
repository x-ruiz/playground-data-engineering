package x.ruiz.playground.data.engineering.lakehouse.core.manager;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidNamespaceSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.NamespaceSpec;

import java.nio.file.Path;

/**
 * Engine-agnostic Template-Method base class for managing lakehouse namespaces from declarative
 * spec files and checking namespace existence.
 *
 * <p>File parsing and null-spec validation are enforced in the {@code final} public template methods
 * so calling libraries never interact with internal file parsers directly and subclasses implement
 * only {@link #doCreateNamespace(NamespaceSpec)} and {@link #doNamespaceExists(NamespaceSpec)}.
 */
public abstract class NamespaceManager {

    private final FileSpecManager fileSpecManager;

    /**
     * Constructs a {@code NamespaceManager} backed by the default internal YAML spec file manager.
     */
    protected NamespaceManager() {
        this(YamlFileSpecManager.INSTANCE);
    }

    /**
     * Package-private constructor allowing internal tests and future format routers to supply
     * a custom {@link FileSpecManager}.
     *
     * @param fileSpecManager non-null file spec manager used to parse namespace definitions
     * @throws IllegalArgumentException if {@code fileSpecManager} is null
     */
    NamespaceManager(FileSpecManager fileSpecManager) {
        if (fileSpecManager == null) {
            throw new IllegalArgumentException("File spec manager must not be null");
        }
        this.fileSpecManager = fileSpecManager;
    }

    /**
     * Parses and validates a declarative namespace specification file, then delegates creation to
     * {@link #doCreateNamespace(NamespaceSpec)}.
     *
     * <p>If file parsing or spec validation fails, {@link #doCreateNamespace(NamespaceSpec)} is never invoked.
     *
     * @param sourcePath path to the namespace specification file
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.SpecParseException
     *         if {@code sourcePath} is null, unreadable, empty, malformed, or contains unknown fields
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogSpecException
     *         if the catalog identifier is invalid
     * @throws InvalidNamespaceSpecException
     *         if the namespace definition is invalid or the internal file manager returns {@code null}
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException
     *         if the target catalog has not been provisioned by the platform
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException
     *         if the underlying catalog engine fails unexpectedly
     */
    public final void createNamespace(Path sourcePath) {
        NamespaceSpec spec = fileSpecManager.parseNamespace(sourcePath);
        if (spec == null) {
            throw new InvalidNamespaceSpecException("Parsed namespace spec must not be null");
        }
        doCreateNamespace(spec);
    }

    /**
     * Parses a declarative namespace specification file and checks whether the target namespace exists.
     *
     * @param sourcePath path to the namespace specification file
     * @return {@code true} if the namespace currently exists in the target catalog, {@code false} otherwise
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.SpecParseException
     *         if {@code sourcePath} is null, unreadable, empty, malformed, or contains unknown fields
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogSpecException
     *         if the catalog identifier is invalid
     * @throws InvalidNamespaceSpecException
     *         if the namespace definition is invalid or the internal file manager returns {@code null}
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException
     *         if the target catalog has not been provisioned by the platform
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException
     *         if the underlying catalog engine fails unexpectedly
     */
    public final boolean namespaceExists(Path sourcePath) {
        NamespaceSpec spec = fileSpecManager.parseNamespace(sourcePath);
        return namespaceExists(spec);
    }

    /**
     * Checks whether the namespace identified by {@code spec} currently exists in its catalog.
     *
     * @param spec non-null namespace specification
     * @return {@code true} if the namespace exists, {@code false} otherwise
     * @throws InvalidNamespaceSpecException if {@code spec} is null
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException
     *         if {@code spec.catalog()} has not been provisioned by the platform
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException
     *         if the underlying catalog engine fails unexpectedly
     */
    public final boolean namespaceExists(NamespaceSpec spec) {
        if (spec == null) {
            throw new InvalidNamespaceSpecException("Namespace spec must not be null");
        }
        return doNamespaceExists(spec);
    }

    /**
     * Engine-specific hook to create the namespace represented by a validated {@link NamespaceSpec}.
     *
     * <p>Implementations must be idempotent (no-op if the namespace already exists) and must throw
     * {@link x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException}
     * if {@code spec.catalog()} does not exist—never auto-creating missing catalogs.
     *
     * @param spec non-null, pre-validated namespace specification
     */
    protected abstract void doCreateNamespace(NamespaceSpec spec);

    /**
     * Engine-specific hook to test whether a validated {@link NamespaceSpec} exists in its catalog.
     *
     * <p>Implementations must fail fast with
     * {@link x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException}
     * if {@code spec.catalog()} has not been provisioned by the platform.
     *
     * @param spec non-null, pre-validated namespace specification
     * @return {@code true} if the namespace exists, {@code false} otherwise
     */
    protected abstract boolean doNamespaceExists(NamespaceSpec spec);
}
