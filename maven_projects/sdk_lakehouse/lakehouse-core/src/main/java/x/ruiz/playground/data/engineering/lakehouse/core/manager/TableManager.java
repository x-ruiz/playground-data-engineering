package x.ruiz.playground.data.engineering.lakehouse.core.manager;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.TableSpec;

import java.nio.file.Path;

/**
 * Engine-agnostic Template-Method base class for managing lakehouse tables from declarative spec files.
 *
 * <p>File parsing and domain validation are encapsulated in {@link #createTable(Path)} so calling
 * libraries never interact with internal file parsers directly and engine subclasses cannot bypass
 * validation. Subclasses implement only {@link #doCreateTable(TableSpec)}.
 */
public abstract class TableManager {

    private final FileSpecManager fileSpecManager;

    /**
     * Constructs a {@code TableManager} backed by the default internal YAML spec file manager.
     */
    protected TableManager() {
        this(YamlFileSpecManager.INSTANCE);
    }

    /**
     * Package-private constructor allowing internal tests and future format routers to supply
     * a custom {@link FileSpecManager}.
     *
     * @param fileSpecManager non-null file spec manager used to parse table definitions
     * @throws IllegalArgumentException if {@code fileSpecManager} is null
     */
    TableManager(FileSpecManager fileSpecManager) {
        if (fileSpecManager == null) {
            throw new IllegalArgumentException("File spec manager must not be null");
        }
        this.fileSpecManager = fileSpecManager;
    }

    /**
     * Parses and validates a declarative table specification file, then delegates creation to
     * {@link #doCreateTable(TableSpec)}.
     *
     * <p>If file parsing or spec validation fails, {@link #doCreateTable(TableSpec)} is never invoked.
     *
     * @param sourcePath path to the table specification file
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.SpecParseException
     *         if {@code sourcePath} is null, unreadable, empty, malformed, or contains unknown fields
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogSpecException
     *         if the catalog identifier is invalid
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidNamespaceSpecException
     *         if the namespace identifier is invalid
     * @throws InvalidTableSpecException
     *         if the table definition is invalid or the internal file manager returns {@code null}
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException
     *         if the target catalog has not been provisioned by the platform
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.NamespaceNotFoundException
     *         if the target namespace does not exist
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException
     *         if the underlying catalog or storage engine fails unexpectedly
     */
    public final void createTable(Path sourcePath) {
        TableSpec spec = fileSpecManager.parseTable(sourcePath);
        if (spec == null) {
            throw new InvalidTableSpecException("Parsed table spec must not be null");
        }
        doCreateTable(spec);
    }

    /**
     * Engine-specific hook to create the table represented by a validated {@link TableSpec}.
     *
     * <p>Implementations must be idempotent (no-op if the table already exists), must throw
     * {@link x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException}
     * if {@code spec.catalog()} does not exist, and must throw
     * {@link x.ruiz.playground.data.engineering.lakehouse.core.exception.NamespaceNotFoundException}
     * if {@code spec.namespace()} does not exist—never auto-creating missing catalogs or namespaces.
     *
     * @param spec non-null, pre-validated table specification
     */
    protected abstract void doCreateTable(TableSpec spec);
}
