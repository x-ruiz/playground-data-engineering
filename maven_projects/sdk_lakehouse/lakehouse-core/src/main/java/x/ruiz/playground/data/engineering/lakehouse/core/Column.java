package x.ruiz.playground.data.engineering.lakehouse.core;

public record Column(
        String name,
        String type,
        Boolean nullable
) {}
