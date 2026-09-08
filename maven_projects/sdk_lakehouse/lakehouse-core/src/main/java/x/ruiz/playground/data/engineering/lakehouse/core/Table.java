package x.ruiz.playground.data.engineering.lakehouse.core;

public interface Table {
    String name();
    String namespace();
    String schema();
}
