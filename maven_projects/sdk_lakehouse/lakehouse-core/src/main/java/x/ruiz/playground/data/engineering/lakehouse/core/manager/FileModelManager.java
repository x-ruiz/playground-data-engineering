package x.ruiz.playground.data.engineering.lakehouse.core.manager;

import x.ruiz.playground.data.engineering.lakehouse.core.model.NamespaceModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.TableModel;

import java.nio.file.Path;

/**
 * Package-private contract for reading declarative specification files into validated domain specs.
 *
 * <p>Kept package-private within {@code ...core.manager} so calling libraries and engine adapters
 * cannot bypass {@link TableManager} and {@link NamespaceManager} to parse spec files directly.
 */
interface FileModelManager {

    /**
     * Parses a table specification file into a validated {@link TableModel}.
     *
     * @param sourcePath path to the table specification file
     * @return validated {@link TableModel}
     */
    TableModel parseTable(Path sourcePath);

    /**
     * Parses a namespace specification file into a validated {@link NamespaceModel}.
     *
     * @param sourcePath path to the namespace specification file
     * @return validated {@link NamespaceModel}
     */
    NamespaceModel parseNamespace(Path sourcePath);

    /**
     * Reads and deserializes a specification file into an intermediate representation of {@code targetType}.
     *
     * @param sourcePath path to the specification file
     * @param targetType target DTO or wire-message class to deserialize into
     * @param modelKind   human-readable spec category (for example, {@code "table"} or {@code "namespace"})
     *                   used in logs and error messages
     * @param <D>        deserialized intermediate type
     * @return non-null deserialized instance
     */
    <D> D readFile(Path sourcePath, Class<D> targetType, String modelKind);
}
