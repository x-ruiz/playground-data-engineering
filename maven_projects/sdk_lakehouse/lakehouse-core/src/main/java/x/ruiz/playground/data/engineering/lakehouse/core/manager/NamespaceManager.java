package x.ruiz.playground.data.engineering.lakehouse.core.manager;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidNamespaceModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.model.NamespaceModel;

import java.nio.file.Path;

/**
 * Engine-agnostic Template-Method base class for managing lakehouse namespaces from declarative
 * spec files and checking namespace existence.
 *
 * <p>File parsing and null-spec validation are enforced in the {@code final} public template methods
 * so calling libraries never interact with internal file parsers directly and subclasses implement
 * only {@link #doCreateNamespace(NamespaceModel)} and {@link #doNamespaceExists(NamespaceModel)}.
 */
public abstract class NamespaceManager {

    private final FileModelManager fileModelManager;

    /**
     * Constructs a {@code NamespaceManager} backed by the default internal YAML spec file manager.
     */
    protected NamespaceManager() {
        this(YamlFileModelManager.INSTANCE);
    }

    /**
     * Package-private constructor allowing internal tests and future format routers to supply
     * a custom {@link FileModelManager}.
     *
     * @param fileModelManager non-null file spec manager used to parse namespace definitions
     * @throws IllegalArgumentException if {@code fileModelManager} is null
     */
    NamespaceManager(FileModelManager fileModelManager) {
        if (fileModelManager == null) {
            throw new IllegalArgumentException("File model manager must not be null");
        }
        this.fileModelManager = fileModelManager;
    }

    /**
     * Parses and validates a declarative namespace specification file, then delegates creation to
     * {@link #doCreateNamespace(NamespaceModel)}.
     *
     * <p>If file parsing or spec validation fails, {@link #doCreateNamespace(NamespaceModel)} is never invoked.
     *
     * @param sourcePath path to the namespace specification file
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.ModelParseException
     *         if {@code sourcePath} is null, unreadable, empty, malformed, or contains unknown fields
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException
     *         if the catalog identifier is invalid
     * @throws InvalidNamespaceModelException
     *         if the namespace definition is invalid or the internal file manager returns {@code null}
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException
     *         if the target catalog has not been provisioned by the platform
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException
     *         if the underlying catalog engine fails unexpectedly
     */
    public final void createNamespace(Path sourcePath) {
        NamespaceModel spec = fileModelManager.parseNamespace(sourcePath);
        if (spec == null) {
            throw new InvalidNamespaceModelException("Parsed namespace model must not be null");
        }
        doCreateNamespace(spec);
    }

    /**
     * Parses a declarative namespace specification file and checks whether the target namespace exists.
     *
     * @param sourcePath path to the namespace specification file
     * @return {@code true} if the namespace currently exists in the target catalog, {@code false} otherwise
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.ModelParseException
     *         if {@code sourcePath} is null, unreadable, empty, malformed, or contains unknown fields
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException
     *         if the catalog identifier is invalid
     * @throws InvalidNamespaceModelException
     *         if the namespace definition is invalid or the internal file manager returns {@code null}
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException
     *         if the target catalog has not been provisioned by the platform
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException
     *         if the underlying catalog engine fails unexpectedly
     */
    public final boolean namespaceExists(Path sourcePath) {
        NamespaceModel spec = fileModelManager.parseNamespace(sourcePath);
        return namespaceExists(spec);
    }

    /**
     * Checks whether the namespace identified by {@code spec} currently exists in its catalog.
     *
     * @param spec non-null namespace specification
     * @return {@code true} if the namespace exists, {@code false} otherwise
     * @throws InvalidNamespaceModelException if {@code spec} is null
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException
     *         if {@code spec.catalog()} has not been provisioned by the platform
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException
     *         if the underlying catalog engine fails unexpectedly
     */
    public final boolean namespaceExists(NamespaceModel spec) {
        if (spec == null) {
            throw new InvalidNamespaceModelException("Namespace model must not be null");
        }
        return doNamespaceExists(spec);
    }

    /**
     * Engine-specific hook to create the namespace represented by a validated {@link NamespaceModel}.
     *
     * <p>Implementations must be idempotent (no-op if the namespace already exists) and must throw
     * {@link x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException}
     * if {@code spec.catalog()} does not exist—never auto-creating missing catalogs.
     *
     * @param spec non-null, pre-validated namespace specification
     */
    protected abstract void doCreateNamespace(NamespaceModel spec);

    /**
     * Engine-specific hook to test whether a validated {@link NamespaceModel} exists in its catalog.
     *
     * <p>Implementations must fail fast with
     * {@link x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException}
     * if {@code spec.catalog()} has not been provisioned by the platform.
     *
     * @param spec non-null, pre-validated namespace specification
     * @return {@code true} if the namespace exists, {@code false} otherwise
     */
    protected abstract boolean doNamespaceExists(NamespaceModel spec);
}
