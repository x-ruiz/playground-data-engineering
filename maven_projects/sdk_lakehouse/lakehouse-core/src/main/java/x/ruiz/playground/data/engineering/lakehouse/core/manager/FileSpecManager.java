package x.ruiz.playground.data.engineering.lakehouse.core.manager;

import x.ruiz.playground.data.engineering.lakehouse.core.spec.NamespaceSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.spec.TableSpec;

import java.nio.file.Path;

/**
 * Package-private contract for reading declarative specification files into validated domain specs.
 *
 * <p>Kept package-private within {@code ...core.manager} so calling libraries and engine adapters
 * cannot bypass {@link TableManager} and {@link NamespaceManager} to parse spec files directly.
 */
interface FileSpecManager {

    /**
     * Parses a table specification file into a validated {@link TableSpec}.
     *
     * @param sourcePath path to the table specification file
     * @return validated {@link TableSpec}
     */
    TableSpec parseTable(Path sourcePath);

    /**
     * Parses a namespace specification file into a validated {@link NamespaceSpec}.
     *
     * @param sourcePath path to the namespace specification file
     * @return validated {@link NamespaceSpec}
     */
    NamespaceSpec parseNamespace(Path sourcePath);

    /**
     * Reads and deserializes a specification file into an intermediate representation of {@code targetType}.
     *
     * @param sourcePath path to the specification file
     * @param targetType target DTO or wire-message class to deserialize into
     * @param specKind   human-readable spec category (for example, {@code "table"} or {@code "namespace"})
     *                   used in logs and error messages
     * @param <D>        deserialized intermediate type
     * @return non-null deserialized instance
     */
    <D> D readFile(Path sourcePath, Class<D> targetType, String specKind);
}
