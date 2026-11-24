package x.ruiz.playground.data.engineering.lakehouse.core.spec;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.LakehouseException;

import java.util.function.Function;
import java.util.regex.Pattern;

public sealed interface LakehouseSpec permits CatalogSpec, NamespaceSpec, TableSpec {

    Pattern VALID_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    String name();

    String fullyQualifiedName();

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
