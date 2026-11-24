package x.ruiz.playground.data.engineering.lakehouse.core.manager;

import x.ruiz.playground.data.engineering.lakehouse.core.spec.NamespaceSpec;

public interface NamespaceManager {

    void createNamespace(NamespaceSpec spec);

    boolean namespaceExists(NamespaceSpec spec);
}
