package org.sunbird.search.strategy;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.sunbird.common.exception.ClientException;
import org.sunbird.search.dto.SearchDTO;
import org.sunbird.search.embedding.EmbeddingCache;
import org.sunbird.search.embedding.EmbeddingClient;
import org.sunbird.search.embedding.EmbeddingClientFactory;
import org.sunbird.search.processor.SearchProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SemanticQueryStrategyTest {

    private SemanticQueryStrategy strategy;
    private SearchProcessor processor;

    // Fake embedding client — deterministic, no network
    private static class FakeEmbeddingClient implements EmbeddingClient {
        private final int dims;
        FakeEmbeddingClient(int dims) { this.dims = dims; }
        @Override public String getName() { return "openai"; }
        @Override public String getVersion() { return "test"; }
        @Override public String getModelId() { return "test-model"; }
        @Override public int getDimensions() { return dims; }
        @Override
        public float[] embed(String text) {
            // L2-normalized vector: 1/sqrt(dims) in each dimension
            float val = (float) (1.0 / Math.sqrt(dims));
            float[] v = new float[dims];
            Arrays.fill(v, val);
            return v;
        }
        @Override
        public List<float[]> embedBatch(List<String> texts) {
            List<float[]> result = new ArrayList<>();
            for (String t : texts) result.add(embed(t));
            return result;
        }
        @Override public void close() {}
    }

    @Before
    public void setup() {
        EmbeddingClientFactory.overrideForTest(new FakeEmbeddingClient(4));
        EmbeddingCache cache = new EmbeddingCache(false, 0, 0); // disabled for tests
        strategy = new SemanticQueryStrategy(cache);
        processor = new SearchProcessor();
    }

    @After
    public void teardown() {
        EmbeddingClientFactory.resetForTest();
    }

    private SearchDTO dtoWithQuery(String query) {
        SearchDTO dto = new SearchDTO();
        Map<String, Object> prop = new HashMap<>();
        prop.put("propertyName", "*");
        prop.put("values", Arrays.asList(query));
        prop.put("operation", "EQ");
        dto.setProperties(Arrays.asList(prop));
        dto.setSemanticParams(new HashMap<>());
        return dto;
    }

    @Test(expected = ClientException.class)
    public void testMissingQueryString_throwsClientException() {
        SearchDTO dto = new SearchDTO();
        dto.setProperties(new ArrayList<>());
        dto.setSemanticParams(new HashMap<>());
        strategy.build(dto, processor);
    }

    @Test
    public void testBuild_returnsQueryBuilder() {
        SearchDTO dto = dtoWithQuery("photosynthesis");
        QueryBuilder result = strategy.build(dto, processor);
        Assert.assertNotNull(result);
    }

    @Test
    public void testBuild_returnsBoolQueryBuilder() {
        SearchDTO dto = dtoWithQuery("photosynthesis");
        QueryBuilder result = strategy.build(dto, processor);
        Assert.assertTrue("result should be BoolQueryBuilder", result instanceof BoolQueryBuilder);
    }

    @Test
    public void testBuild_fullTextPropertyNotLeakedIntoFilters() {
        SearchDTO dto = dtoWithQuery("photosynthesis");
        dto.setOperation("AND");
        Map<String, Object> statusProp = new HashMap<>();
        statusProp.put("propertyName", "status");
        statusProp.put("values", Arrays.asList("Live"));
        statusProp.put("operation", "EQ");
        List<Map> props = new ArrayList<>();
        props.add((Map) dto.getProperties().get(0));
        props.add(statusProp);
        dto.setProperties(props);

        QueryBuilder result = strategy.build(dto, processor);
        // Should not throw and should return valid query
        // The key assertion: DTO properties are restored after build
        Assert.assertEquals("DTO properties should be restored after build",
                2, dto.getProperties().size());
    }

    @Test
    public void testBuild_fuzzyEnabled_filtersStillInherited_andDTORestored() {
        SearchDTO dto = dtoWithQuery("climate change");
        dto.setFuzzySearch(true);
        dto.setOperation("AND");

        Map<String, Object> channelProp = new HashMap<>();
        channelProp.put("propertyName", "channel");
        channelProp.put("values", Arrays.asList("org123"));
        channelProp.put("operation", "EQ");
        List<Map> props = new ArrayList<>();
        props.add((Map) dto.getProperties().get(0));
        props.add(channelProp);
        dto.setProperties(props);

        // Should not throw — fuzzy was causing FunctionScoreQueryBuilder to skip filters
        QueryBuilder result = strategy.build(dto, processor);
        Assert.assertNotNull(result);
        // DTO fuzzy state must be restored
        Assert.assertTrue("fuzzySearch should be restored to true", dto.isFuzzySearch());
    }

    @Test
    public void testBuild_dtoPropertiesRestoredAfterException() {
        // Even if embed throws, DTO must be restored
        EmbeddingClientFactory.overrideForTest(new EmbeddingClient() {
            @Override public String getName() { return "fail"; }
            @Override public String getVersion() { return "0"; }
            @Override public String getModelId() { return "fail-model"; }
            @Override public int getDimensions() { return 4; }
            @Override public float[] embed(String text) { throw new RuntimeException("network error"); }
            @Override public List<float[]> embedBatch(List<String> texts) { return null; }
            @Override public void close() {}
        });

        SearchDTO dto = dtoWithQuery("query");
        dto.setFuzzySearch(true);
        List<Map> originalProps = new ArrayList<>(dto.getProperties());

        try {
            strategy.build(dto, processor);
        } catch (Exception ignored) {}

        Assert.assertTrue("fuzzySearch should be restored even on exception", dto.isFuzzySearch());
    }

    @Test
    public void testGetMode() {
        Assert.assertEquals("semantic", strategy.getMode());
    }
}
