package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IcebergPartitionSpecHelper {
    private static final Pattern BUCKET_PATTERN = Pattern.compile("bucket\\s*\\(\\s*(\\d+)\\s*,\\s*([a-zA-Z0-9_]+)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRUNCATE_PATTERN = Pattern.compile("truncate\\s*\\(\\s*(\\d+)\\s*,\\s*([a-zA-Z0-9_]+)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSFORM_PATTERN = Pattern.compile("(year|month|day|hour)\\s*\\(\\s*([a-zA-Z0-9_]+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    private IcebergPartitionSpecHelper() {}

    /**
     * Builds an Iceberg PartitionSpec given a Schema and a list of partition expressions.
     */
    public static PartitionSpec buildPartitionSpec(Schema schema, List<String> partitionExpressions) {
        if (partitionExpressions == null || partitionExpressions.isEmpty()) {
            return PartitionSpec.unpartitioned();
        }

        PartitionSpec.Builder builder = PartitionSpec.builderFor(schema);

        for (String expr : partitionExpressions) {
            String trimmed = expr.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            Matcher bucketMatcher = BUCKET_PATTERN.matcher(trimmed);
            if (bucketMatcher.matches()) {
                int numBuckets = Integer.parseInt(bucketMatcher.group(1));
                String col = bucketMatcher.group(2);
                builder.bucket(col, numBuckets);
                continue;
            }

            Matcher truncateMatcher = TRUNCATE_PATTERN.matcher(trimmed);
            if (truncateMatcher.matches()) {
                int width = Integer.parseInt(truncateMatcher.group(1));
                String col = truncateMatcher.group(2);
                builder.truncate(col, width);
                continue;
            }

            Matcher transformMatcher = TRANSFORM_PATTERN.matcher(trimmed);
            if (transformMatcher.matches()) {
                String transform = transformMatcher.group(1).toLowerCase(Locale.ROOT);
                String col = transformMatcher.group(2);
                switch (transform) {
                    case "year" -> builder.year(col);
                    case "month" -> builder.month(col);
                    case "day" -> builder.day(col);
                    case "hour" -> builder.hour(col);
                    default -> throw new IllegalArgumentException("Unknown transform: " + transform);
                }
                continue;
            }

            // Default to identity partition
            builder.identity(trimmed);
        }

        return builder.build();
    }
}
