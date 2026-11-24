package x.ruiz.playground.data.engineering.lakehouse.core;

public interface NamespaceManager {

    void createNamespace(NamespaceSpec spec);

    boolean namespaceExists(NamespaceSpec spec);
}
