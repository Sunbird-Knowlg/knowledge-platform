package org.sunbird.search.embedding;

/**
 * Plain config object passed to embedding clients. Mirrors the embedding
 * job's EmbeddingServiceConfig so operators can copy values across without
 * translation.
 */
public class EmbeddingClientConfig {

    private final String  serviceName;
    private final int     dimensions;
    private final int     timeoutSeconds;
    private final String  host;            // e5 only
    private final Integer port;            // e5 only
    private final String  apiKey;          // openai only
    private final String  model;           // openai only
    private final String  azureEndpoint;   // openai only — empty for standard OpenAI
    private final String  azureDeployment; // openai only
    private final String  azureApiVersion; // openai only

    private EmbeddingClientConfig(Builder b) {
        this.serviceName     = b.serviceName;
        this.dimensions      = b.dimensions;
        this.timeoutSeconds  = b.timeoutSeconds;
        this.host            = b.host;
        this.port            = b.port;
        this.apiKey          = b.apiKey;
        this.model           = b.model;
        this.azureEndpoint   = b.azureEndpoint;
        this.azureDeployment = b.azureDeployment;
        this.azureApiVersion = b.azureApiVersion;
    }

    public String  getServiceName()     { return serviceName; }
    public int     getDimensions()      { return dimensions; }
    public int     getTimeoutSeconds()  { return timeoutSeconds; }
    public String  getHost()            { return host; }
    public Integer getPort()            { return port; }
    public String  getApiKey()          { return apiKey; }
    public String  getModel()           { return model; }
    public String  getAzureEndpoint()   { return azureEndpoint; }
    public String  getAzureDeployment() { return azureDeployment; }
    public String  getAzureApiVersion() { return azureApiVersion; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String  serviceName;
        private int     dimensions     = 1536;
        private int     timeoutSeconds = 5;
        private String  host;
        private Integer port;
        private String  apiKey;
        private String  model;
        private String  azureEndpoint;
        private String  azureDeployment;
        private String  azureApiVersion;

        public Builder serviceName(String v)     { this.serviceName = v;     return this; }
        public Builder dimensions(int v)         { this.dimensions = v;      return this; }
        public Builder timeoutSeconds(int v)     { this.timeoutSeconds = v;  return this; }
        public Builder host(String v)            { this.host = v;            return this; }
        public Builder port(Integer v)           { this.port = v;            return this; }
        public Builder apiKey(String v)          { this.apiKey = v;          return this; }
        public Builder model(String v)           { this.model = v;           return this; }
        public Builder azureEndpoint(String v)   { this.azureEndpoint = v;   return this; }
        public Builder azureDeployment(String v) { this.azureDeployment = v; return this; }
        public Builder azureApiVersion(String v) { this.azureApiVersion = v; return this; }

        public EmbeddingClientConfig build() { return new EmbeddingClientConfig(this); }
    }
}
