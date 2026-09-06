package x.ruiz.playground.data.engineering.lakehouse.models;

public interface Table {
    String name();
    String namespace();
    String schema();
}
