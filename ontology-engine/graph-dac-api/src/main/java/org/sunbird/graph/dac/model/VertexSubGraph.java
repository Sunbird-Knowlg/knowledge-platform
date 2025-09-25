package org.sunbird.graph.dac.model;

import java.util.List;
import java.util.Map;

public class VertexSubGraph {
    private Map<String, Vertex> vertexs;
    private List<Edges> edges;

    public VertexSubGraph(Map<String, Vertex> vertexs, List<Edges> edges) {
        this.vertexs = vertexs;
        this.edges = edges;
    }

    public Map<String, Vertex> getVertexs() { return vertexs; }

    public void setVertexs(Map<String, Vertex> vertexs) {
        this.vertexs = vertexs;
    }

    public List<Edges> getEdges() {
        return edges;
    }

    public void setEdges(List<Edges> edges) {
        this.edges = edges;
    }
}
