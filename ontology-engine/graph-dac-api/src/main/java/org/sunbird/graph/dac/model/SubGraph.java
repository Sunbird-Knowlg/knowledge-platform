package org.sunbird.graph.dac.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SubGraph implements Serializable {

    private Map<String, Node> nodes;
    private List<Relation> relations;

    public SubGraph(Map<String, Node> nodes, List<Relation> relations) {
        this.nodes = nodes;
        this.relations = relations;
    }

    public Map<String, Node> getNodes() {
        return nodes;
    }

    public void setNodes(Map<String, Node> nodes) {
        this.nodes = nodes;
    }

    public List<Relation> getRelations() {
        return relations;
    }

    public void setRelations(List<Relation> relations) {
        this.relations = relations;
    }
}
