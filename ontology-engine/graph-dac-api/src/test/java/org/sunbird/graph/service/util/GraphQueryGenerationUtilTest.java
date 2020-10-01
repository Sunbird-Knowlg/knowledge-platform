package org.sunbird.graph.service.util;

import org.junit.Assert;
import org.junit.Test;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.sunbird.common.dto.Request;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.GraphDACParams;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.enums.RelationTypes;
import org.sunbird.graph.dac.model.Node;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class GraphQueryGenerationUtilTest {

    @Test
    public void testgenerateCreateUniqueConstraintCypherQuery_1() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.indexProperty.name(),"identifier");
        }};
        String query = GraphQueryGenerationUtil.generateCreateUniqueConstraintCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("CREATE CONSTRAINT ON (n:domain) ASSERT n.identifier IS UNIQUE ", query);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateUniqueConstraintCypherQuery_2() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.indexProperty.name(),"identifier");
        }};
        GraphQueryGenerationUtil.generateCreateUniqueConstraintCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateUniqueConstraintCypherQuery_3() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
        }};
        GraphQueryGenerationUtil.generateCreateUniqueConstraintCypherQuery(params);
    }

    @Test
    public void testgenerateCreateIndexCypherQuery_1() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.indexProperty.name(),"identifier");
        }};
        String query = GraphQueryGenerationUtil.generateCreateIndexCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("CREATE INDEX ON :domain(identifier) ", query);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateIndexCypherQuery_2() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.indexProperty.name(),"identifier");
        }};
        GraphQueryGenerationUtil.generateCreateIndexCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateIndexCypherQuery_3() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
        }};
        GraphQueryGenerationUtil.generateCreateIndexCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateDeleteGraphCypherQuery_1() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.indexProperty.name(),"identifier");
        }};
        GraphQueryGenerationUtil.generateDeleteGraphCypherQuery(params);
    }

    @Test
    public void testgenerateDeleteGraphCypherQuery_2() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.indexProperty.name(),"identifier");
        }};
        String query = GraphQueryGenerationUtil.generateDeleteGraphCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("MATCH (n) REMOVE n:domain", query);
    }

    @Test(expected = ServerException.class)
    public void testgenerateCreateRelationCypherQuery_1() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        String query = GraphQueryGenerationUtil.generateCreateRelationCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("", query);
    }

    @Test
    public void testgenerateCreateRelationCypherQuery_2() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        String query = GraphQueryGenerationUtil.generateCreateRelationCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("", query);
    }

    @Test(expected = ServerException.class)
    public void testgenerateCreateRelationCypherQuery_3() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateCreateRelationCypherQuery(params);
    }

    @Test(expected = ServerException.class)
    public void testgenerateCreateRelationCypherQuery_4() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateCreateRelationCypherQuery(params);
    }

    @Test(expected = ServerException.class)
    public void testgenerateCreateRelationCypherQuery_5() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateCreateRelationCypherQuery(params);
    }

    @Test(expected = ServerException.class)
    public void testgenerateCreateRelationCypherQuery_6() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateCreateRelationCypherQuery(params);
    }

    @Test
    public void testgenerateUpdateRelationCypherQuery_1() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        String query = GraphQueryGenerationUtil.generateUpdateRelationCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("", query);
    }

    @Test
    public void testgenerateUpdateRelationCypherQuery_2() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        String query = GraphQueryGenerationUtil.generateUpdateRelationCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("", query);
    }

    @Test(expected = ClientException.class)
    public void testgenerateUpdateRelationCypherQuery_3() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateUpdateRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateUpdateRelationCypherQuery_4() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateUpdateRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateUpdateRelationCypherQuery_5() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateUpdateRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateUpdateRelationCypherQuery_6() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateUpdateRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateUpdateRelationCypherQuery_7() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), null);

        }};
        GraphQueryGenerationUtil.generateUpdateRelationCypherQuery(params);
    }

    @Test
    public void generateDeleteRelationCypherQuery_1() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        String query = GraphQueryGenerationUtil.generateDeleteRelationCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("MATCH (a:domain {IL_UNIQUE_ID: 'do_1234'})-[r:hasSequenceMember]->(b:domain {IL_UNIQUE_ID: 'do_3456'}) DELETE r ", query);
    }

    @Test
    public void testgenerateDeleteRelationCypherQuery_2() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        String query = GraphQueryGenerationUtil.generateDeleteRelationCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("MATCH (a:domain {IL_UNIQUE_ID: 'do_1234'})-[r:associatedTo]->(b:domain {IL_UNIQUE_ID: 'do_3456'}) DELETE r ", query);
    }

    @Test(expected = ClientException.class)
    public void testgenerateDeleteRelationCypherQuery_3() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateDeleteRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateDeleteRelationCypherQuery_4() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateDeleteRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateDeleteRelationCypherQuery_5() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateDeleteRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateDeleteRelationCypherQuery_6() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_1234");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateDeleteRelationCypherQuery(params);
    }

    @Test
    public void generateCreateIncomingRelationCypherQuery_1() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeIds.name(), Arrays.asList("do_1234"));
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        String query = GraphQueryGenerationUtil.generateCreateIncomingRelationCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("", query);
    }

    @Test
    public void testgenerateCreateIncomingRelationCypherQuery_2() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        String query = GraphQueryGenerationUtil.generateCreateIncomingRelationCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("MATCH (B:domain { IL_UNIQUE_ID: 'do_12345' }),(C:domain { IL_UNIQUE_ID: 'do_3456' }) MERGE (B)<-[r:associatedTo]-(C)ON CREATE SET r.IL_SEQUENCE_INDEX='1234567' ON MATCH SET r.IL_SEQUENCE_INDEX='1234567' ", query);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateIncomingRelationCypherQuery_3() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.startNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateCreateIncomingRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateIncomingRelationCypherQuery_4() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateCreateIncomingRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateIncomingRelationCypherQuery_5() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateCreateIncomingRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateIncomingRelationCypherQuery_6() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateCreateIncomingRelationCypherQuery(params);
    }

    @Test
    public void testgenerateCreateOutgoingRelationCypherQuery_1() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(), "do_1234");
            put(GraphDACParams.endNodeIds.name(),Arrays.asList("do_3456"));
            put(GraphDACParams.relationType.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        String query = GraphQueryGenerationUtil.generateCreateOutgoingRelationCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("", query);
    }

    @Test
    public void testgenerateCreateOutgoingRelationCypherQuery_2() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.endNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.startNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        String query = GraphQueryGenerationUtil.generateCreateOutgoingRelationCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("MATCH (B:domain { IL_UNIQUE_ID: 'do_3456' }),(C:domain { IL_UNIQUE_ID: 'do_12345' }) MERGE (B)-[r:associatedTo]->(C)ON CREATE SET r.IL_SEQUENCE_INDEX='1234567' ON MATCH SET r.IL_SEQUENCE_INDEX='1234567' ", query);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateOutgoingRelationCypherQuery_3() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.endNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.startNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateCreateOutgoingRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateOutgoingRelationCypherQuery_4() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.endNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateCreateOutgoingRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateOutgoingRelationCypherQuery_5() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(), "do_1234");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateCreateOutgoingRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateOutgoingRelationCypherQuery_6() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.endNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.startNodeId.name(),"do_3456");
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateCreateOutgoingRelationCypherQuery(params);
    }

    @Test
    public void generateDeleteIncomingRelationCypherQuery_1() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeIds.name(), Arrays.asList("do_1234"));
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        String query = GraphQueryGenerationUtil.generateDeleteIncomingRelationCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("", query);
    }

    @Test
    public void testgenerateDeleteIncomingRelationCypherQuery_2() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        String query = GraphQueryGenerationUtil.generateDeleteIncomingRelationCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("MATCH (a:domain {IL_UNIQUE_ID: 'do_12345'})<-[r:associatedTo]-(b:domain {IL_UNIQUE_ID: 'do_3456'}) DELETE r ", query);
    }

    @Test(expected = ClientException.class)
    public void testgenerateDeleteIncomingRelationCypherQuery_3() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.startNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateDeleteIncomingRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateDeleteIncomingRelationCypherQuery_4() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateDeleteIncomingRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateDeleteIncomingRelationCypherQuery_5() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateDeleteIncomingRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateDeleteIncomingRelationCypherQuery_6() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateDeleteIncomingRelationCypherQuery(params);
    }

    @Test
    public void testgenerateDeleteOutgoingRelationCypherQuery_1() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(), "do_1234");
            put(GraphDACParams.endNodeIds.name(),Arrays.asList("do_3456"));
            put(GraphDACParams.relationType.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        String query = GraphQueryGenerationUtil.generateDeleteOutgoingRelationCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("", query);
    }

    @Test
    public void testgenerateDeleteOutgoingRelationCypherQuery_2() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.endNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.startNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        String query = GraphQueryGenerationUtil.generateDeleteOutgoingRelationCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("MATCH (a:domain {IL_UNIQUE_ID: 'do_3456'})<-[r:associatedTo]-(b:domain {IL_UNIQUE_ID: 'do_12345'}) DELETE r ", query);
    }

    @Test(expected = ClientException.class)
    public void testgenerateDeleteOutgoingRelationCypherQuery_3() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.endNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.startNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateDeleteOutgoingRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateDeleteOutgoingRelationCypherQuery_4() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.endNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateDeleteOutgoingRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateDeleteOutgoingRelationCypherQuery_5() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(), "do_1234");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateDeleteOutgoingRelationCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateDeleteOutgoingRelationCypherQuery_6() {
        Request request = new Request();
        request.setRequest(new HashMap<String, Object>() {{
            put(GraphDACParams.metadata.name(), new HashMap<String, Object>() {{
                put(SystemProperties.IL_SEQUENCE_INDEX.name(), "1234567");
            }});
        }});
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.endNodeIds.name(),Arrays.asList("do_1234", "do_12345"));
            put(GraphDACParams.startNodeId.name(),"do_3456");
            put(GraphDACParams.request.name(), request);

        }};
        GraphQueryGenerationUtil.generateDeleteOutgoingRelationCypherQuery(params);
    }

    @Test
    public void testgenerateRemoveRelationMetadataCypherQuery_1() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_3456");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
            put(GraphDACParams.key.name(), "key");

        }};
        String query = GraphQueryGenerationUtil.generateRemoveRelationMetadataCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("MATCH (a:domain {IL_UNIQUE_ID: 'do_3456'})-[r:hasSequenceMember]->(b:domain {IL_UNIQUE_ID: 'do_3456'}) REMOVE r.key ", query);
    }

    @Test
    public void testgenerateRemoveRelationMetadataCypherQuery_2() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_3456");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.key.name(), "key");

        }};
        String query = GraphQueryGenerationUtil.generateRemoveRelationMetadataCypherQuery(params);
        Assert.assertNotNull(query);
        Assert.assertEquals("MATCH (a:domain {IL_UNIQUE_ID: 'do_3456'})-[r:associatedTo]->(b:domain {IL_UNIQUE_ID: 'do_3456'}) REMOVE r.key ", query);
    }

    @Test(expected = ClientException.class)
    public void testgenerateRemoveRelationMetadataCypherQuery_3() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.startNodeId.name(),"do_3456");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.key.name(), "key");

        }};
        GraphQueryGenerationUtil.generateRemoveRelationMetadataCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateRemoveRelationMetadataCypherQuery_4() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.key.name(), "key");
        }};
        GraphQueryGenerationUtil.generateRemoveRelationMetadataCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateRemoveRelationMetadataCypherQuery_5() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.ASSOCIATED_TO.relationName());
            put(GraphDACParams.key.name(), "key");
        }};
        GraphQueryGenerationUtil.generateRemoveRelationMetadataCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateRemoveRelationMetadataCypherQuery_6() {
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.startNodeId.name(),"do_3456");
            put(GraphDACParams.endNodeId.name(),"do_3456");
            put(GraphDACParams.key.name(), "key");
        }};
        GraphQueryGenerationUtil.generateRemoveRelationMetadataCypherQuery(params);
    }

    @Test
    public void testgenerateCreateCollectionCypherQuery_1() {
        Node node = new Node();
        node.setMetadata(new HashMap<>() {{
            put("name", "test_collection");
            put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(), "12-10-2019");
        }});
        node.setGraphId("domain");
        node.setObjectType("Content");
        node.setIdentifier("do_3456");
        node.setNodeType("DATA_NODE");
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.collectionId.name(),"do_3456");
            put(GraphDACParams.collection.name(), node);
            put(GraphDACParams.relationType.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
            put(GraphDACParams.members.name(), Arrays.asList("keys"));
            put(GraphDACParams.indexProperty.name(), "123456");
        }};
        String query = GraphQueryGenerationUtil.generateCreateCollectionCypherQuery(params);
        Assert.assertNotNull(query);
    }


    @Test(expected = ClientException.class)
    public void testgenerateCreateCollectionCypherQuery_3() {
        Node node = new Node();
        node.setMetadata(new HashMap<>() {{
            put("name", "test_collection");
            put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(), "12-10-2019");
        }});
        node.setGraphId("domain");
        node.setObjectType("Content");
        node.setIdentifier("do_3456");
        node.setNodeType("DATA_NODE");
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.collectionId.name(),"do_3456");
            put(GraphDACParams.collection.name(), node);
            put(GraphDACParams.relationType.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
            put(GraphDACParams.members.name(), Arrays.asList("keys"));
            put(GraphDACParams.indexProperty.name(), "123456");
        }};
        GraphQueryGenerationUtil.generateCreateCollectionCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateCollectionCypherQuery_4() {
        Node node = new Node();
        node.setMetadata(new HashMap<>() {{
            put("name", "test_collection");
            put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(), "12-10-2019");
        }});
        node.setGraphId("domain");
        node.setObjectType("Content");
        node.setIdentifier("do_3456");
        node.setNodeType("DATA_NODE");
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.collection.name(), node);
            put(GraphDACParams.relationType.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
            put(GraphDACParams.members.name(), Arrays.asList("keys"));
            put(GraphDACParams.indexProperty.name(), "123456");
        }};
        GraphQueryGenerationUtil.generateCreateCollectionCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateCollectionCypherQuery_5() {
        Node node = new Node();
        node.setMetadata(new HashMap<>() {{
            put("name", "test_collection");
            put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(), "12-10-2019");
        }});
        node.setGraphId("domain");
        node.setObjectType("Content");
        node.setIdentifier("do_3456");
        node.setNodeType("DATA_NODE");
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.collectionId.name(),"do_3456");
            put(GraphDACParams.relationType.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
            put(GraphDACParams.members.name(), Arrays.asList("keys"));
            put(GraphDACParams.indexProperty.name(), "123456");
        }};
        GraphQueryGenerationUtil.generateCreateCollectionCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateCollectionCypherQuery_6() {
        Node node = new Node();
        node.setMetadata(new HashMap<>() {{
            put("name", "test_collection");
            put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(), "12-10-2019");
        }});
        node.setGraphId("domain");
        node.setObjectType("Content");
        node.setIdentifier("do_3456");
        node.setNodeType("DATA_NODE");
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.collectionId.name(),"do_3456");
            put(GraphDACParams.collection.name(), node);
            put(GraphDACParams.members.name(), Arrays.asList("keys"));
            put(GraphDACParams.indexProperty.name(), "123456");
        }};
        GraphQueryGenerationUtil.generateCreateCollectionCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateCollectionCypherQuery_7() {
        Node node = new Node();
        node.setMetadata(new HashMap<>() {{
            put("name", "test_collection");
            put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(), "12-10-2019");
        }});
        node.setGraphId("domain");
        node.setObjectType("Content");
        node.setIdentifier("do_3456");
        node.setNodeType("DATA_NODE");
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.collectionId.name(),"do_3456");
            put(GraphDACParams.collection.name(), node);
            put(GraphDACParams.relationType.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
            put(GraphDACParams.indexProperty.name(), "123456");
        }};
        GraphQueryGenerationUtil.generateCreateCollectionCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateCreateCollectionCypherQuery_8() {
        Node node = new Node();
        node.setMetadata(new HashMap<>() {{
            put("name", "test_collection");
            put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(), "12-10-2019");
        }});
        node.setGraphId("domain");
        node.setObjectType("Content");
        node.setIdentifier("do_3456");
        node.setNodeType("DATA_NODE");
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.collectionId.name(),"do_3456");
            put(GraphDACParams.collection.name(), node);
            put(GraphDACParams.relationType.name(), RelationTypes.SEQUENCE_MEMBERSHIP.relationName());
            put(GraphDACParams.members.name(), Arrays.asList("keys"));
        }};
        GraphQueryGenerationUtil.generateCreateCollectionCypherQuery(params);
    }

    @Test
    public void testgenerateDeleteCollectionCypherQuery_1() {
        Node node = new Node();
        node.setMetadata(new HashMap<>() {{
            put("name", "test_collection");
            put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(), "12-10-2019");
        }});
        node.setGraphId("domain");
        node.setObjectType("Content");
        node.setIdentifier("do_3456");
        node.setNodeType("DATA_NODE");
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.collectionId.name(),"do_3456");
        }};
        String query = GraphQueryGenerationUtil.generateDeleteCollectionCypherQuery(params);
        Assert.assertNotNull(query);
    }


    @Test(expected = ClientException.class)
    public void testgenerateDeleteCollectionCypherQuery_4() {
        Node node = new Node();
        node.setMetadata(new HashMap<>() {{
            put("name", "test_collection");
            put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(), "12-10-2019");
        }});
        node.setGraphId("domain");
        node.setObjectType("Content");
        node.setIdentifier("do_3456");
        node.setNodeType("DATA_NODE");
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
        }};
        GraphQueryGenerationUtil.generateDeleteCollectionCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateDeleteCollectionCypherQuery_5() {
        Node node = new Node();
        node.setMetadata(new HashMap<>() {{
            put("name", "test_collection");
            put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(), "12-10-2019");
        }});
        node.setGraphId("domain");
        node.setObjectType("Content");
        node.setIdentifier("do_3456");
        node.setNodeType("DATA_NODE");
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.collectionId.name(),"do_3456");
        }};
        GraphQueryGenerationUtil.generateDeleteCollectionCypherQuery(params);
    }

    @Test
    public void testgenerateImportGraphCypherQuery_1() {
        Node node = new Node();
        node.setMetadata(new HashMap<>() {{
            put("name", "test_collection");
            put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(), "12-10-2019");
        }});
        node.setGraphId("domain");
        node.setObjectType("Content");
        node.setIdentifier("do_3456");
        node.setNodeType("DATA_NODE");
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.taskId.name(),"do_3456");
            put(GraphDACParams.input.name(),"input_1332");
        }};
        String query = GraphQueryGenerationUtil.generateImportGraphCypherQuery(params);
        Assert.assertNotNull(query);
    }


    @Test(expected = ClientException.class)
    public void testgenerateImportGraphCypherQuery_4() {
        Node node = new Node();
        node.setMetadata(new HashMap<>() {{
            put("name", "test_collection");
            put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(), "12-10-2019");
        }});
        node.setGraphId("domain");
        node.setObjectType("Content");
        node.setIdentifier("do_3456");
        node.setNodeType("DATA_NODE");
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.input.name(),"input_1332");
        }};
        GraphQueryGenerationUtil.generateImportGraphCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateImportGraphCypherQuery_5() {
        Node node = new Node();
        node.setMetadata(new HashMap<>() {{
            put("name", "test_collection");
            put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(), "12-10-2019");
        }});
        node.setGraphId("domain");
        node.setObjectType("Content");
        node.setIdentifier("do_3456");
        node.setNodeType("DATA_NODE");
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.taskId.name(),"do_3456");
            put(GraphDACParams.input.name(),"input_1332");
        }};
        GraphQueryGenerationUtil.generateImportGraphCypherQuery(params);
    }

    @Test(expected = ClientException.class)
    public void testgenerateImportGraphCypherQuery_6() {
        Node node = new Node();
        node.setMetadata(new HashMap<>() {{
            put("name", "test_collection");
            put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(), "12-10-2019");
        }});
        node.setGraphId("domain");
        node.setObjectType("Content");
        node.setIdentifier("do_3456");
        node.setNodeType("DATA_NODE");
        Map<String, Object> params = new HashMap<>() {{
            put(GraphDACParams.graphId.name(), "domain");
            put(GraphDACParams.taskId.name(),"do_3456");
        }};
        String query = GraphQueryGenerationUtil.generateImportGraphCypherQuery(params);
        Assert.assertNotNull(query);
    }

    @Test
    public void testgenerateCreateBulkRelationsCypherQuery_1() {
        String query = GraphQueryGenerationUtil.generateCreateBulkRelationsCypherQuery("domain");
        Assert.assertNotNull(query);
        Assert.assertEquals("UNWIND {data} AS ROW WITH ROW.startNodeId AS startNode, ROW.endNodeId AS endNode, ROW.relation AS relation, ROW.relMetadata as relMetadata MATCH(n:domain {IL_UNIQUE_ID:startNode}) MATCH(m:domain {IL_UNIQUE_ID:endNode}) \n" +
                "FOREACH (_ IN case WHEN relation='hasSequenceMember' then [1] else[] end| merge (n)-[r:hasSequenceMember]->(m) set r += relMetadata)\n" +
                "FOREACH (_ IN case WHEN relation='associatedTo' then [1] else[] end| merge (n)-[r:associatedTo]->(m) set r += relMetadata)\n" +
                "RETURN COUNT(*) AS RESULT;",query);
    }

    @Test
    public void testgenerateDeleteBulkRelationsCypherQuery_2() {
        String query = GraphQueryGenerationUtil.generateDeleteBulkRelationsCypherQuery("domain");
        Assert.assertNotNull(query);
        Assert.assertEquals("UNWIND {data} AS ROW WITH ROW.startNodeId AS startNode, ROW.endNodeId AS endNode, ROW.relation AS relation, ROW.relMetadata as relMetadata MATCH(n:domain {IL_UNIQUE_ID:startNode})-[r]->(m:domain {IL_UNIQUE_ID:endNode})FOREACH (_ IN case WHEN relation='hasSequenceMember' then [1] else[] end| delete r\n" +
                ")\n" +
                "FOREACH (_ IN case WHEN relation='associatedTo' then [1] else[] end| delete r\n" +
                ")\n" +
                "RETURN COUNT(*) AS RESULT;",query);
    }

}
