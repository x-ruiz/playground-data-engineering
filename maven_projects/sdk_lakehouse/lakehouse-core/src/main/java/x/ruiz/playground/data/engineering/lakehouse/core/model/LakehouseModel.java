package x.ruiz.playground.data.engineering.lakehouse.core.model;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.LakehouseException;

import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Root contract for immutable, self-validating lakehouse object specifications.
 *
 * <p>Implementations form an acyclic, top-down identity hierarchy:
 * {@link CatalogModel} &larr; {@link NamespaceModel} &larr; {@link TableModel}.
 */
public sealed interface LakehouseModel permits CatalogModel, NamespaceModel, TableModel {

    /**
     * Allowed identifier format for catalog, namespace, table, and column names:
     * starts with an ASCII letter or underscore, followed by ASCII letters, digits, or underscores.
     */
    Pattern VALID_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * Returns the unqualified identifier segment of this specification.
     *
     * @return the local name of this spec
     */
    String name();

    /**
     * Returns the dot-delimited fully qualified name of this specification
     * (for example, {@code catalog}, {@code catalog.namespace}, or {@code catalog.namespace.table}).
     *
     * @return the fully qualified identifier
     */
    String fullyQualifiedName();

    /**
     * Validates that an identifier segment is non-null, non-blank, and matches {@link #VALID_IDENTIFIER}.
     *
     * @param fieldName        human-readable field name included in error messages
     * @param value            identifier value to validate
     * @param exceptionFactory factory creating the domain-specific {@link LakehouseException} on failure
     * @throws LakehouseException if {@code value} is null, blank, or violates {@link #VALID_IDENTIFIER}
     */
    static void validateIdentifier(
            String fieldName,
            String value,
            Function<String, ? extends LakehouseException> exceptionFactory) {
        if (value == null || value.isBlank()) {
            throw exceptionFactory.apply(fieldName + " must not be null or blank");
        }
        if (!VALID_IDENTIFIER.matcher(value).matches()) {
            throw exceptionFactory.apply(
                    fieldName + " '" + value + "' must match pattern " + VALID_IDENTIFIER.pattern());
        }
    }
}
