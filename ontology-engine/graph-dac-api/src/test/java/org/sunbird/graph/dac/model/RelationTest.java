package org.sunbird.graph.dac.model;

import org.junit.Assert;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;
import org.sunbird.common.exception.ServerException;
import org.sunbird.test.BaseTest;

import java.util.HashMap;
import java.util.Map;

public class RelationTest extends BaseTest {
    private static String graphId = "domain";
    private Relation relation = new Relation();

    @Test
    public void testRelationModel_1() {
        Relation relation_1 = new Relation();
        Relation relation_2 = new Relation("start_1234", "DATA_NODE", "end_1234");
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],copyright:\"Sunbird\",previewUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_1129067102240194561252-latest\",keywords:[\"Test\"],plugins:\"[{\\\"identifier\\\":\\\"org.ekstep.stage\\\",\\\"semanticVersion\\\":\\\"1.0\\\"},{\\\"identifier\\\":\\\"org.ekstep.shape\\\",\\\"semanticVersion\\\":\\\"1.0\\\"},{\\\"identifier\\\":\\\"org.ekstep.text\\\",\\\"semanticVersion\\\":\\\"1.2\\\"},{\\\"identifier\\\":\\\"org.ekstep.image\\\",\\\"semanticVersion\\\":\\\"1.1\\\"},{\\\"identifier\\\":\\\"org.ekstep.navigation\\\",\\\"semanticVersion\\\":\\\"1.0\\\"}]\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499420_do_1129067102240194561252_2.0.ecar\",channel:\"b00bc992ef25f1a9a8d63291e20efc8d\",organisation:[\"Sunbird\"],language:[\"English\"],variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499615_do_1129067102240194561252_2.0_spine.ecar\\\",\\\"size\\\":36069.0}}\",mimeType:\"application/vnd.ekstep.ecml-archive\",editorState:\"{\\\"plugin\\\":{\\\"noOfExtPlugins\\\":7,\\\"extPlugins\\\":[{\\\"plugin\\\":\\\"org.ekstep.contenteditorfunctions\\\",\\\"version\\\":\\\"1.2\\\"},{\\\"plugin\\\":\\\"org.ekstep.keyboardshortcuts\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.richtext\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.iterator\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.navigation\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.reviewercomments\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.questionunit.ftb\\\",\\\"version\\\":\\\"1.1\\\"}]},\\\"stage\\\":{\\\"noOfStages\\\":5,\\\"currentStage\\\":\\\"c5ead48c-d574-488b-80d0-6d7db2d60637\\\",\\\"selectedPluginObject\\\":\\\"5b6a5e3d-5e44-4254-8c70-d82d6c13cc2c\\\"},\\\"sidebar\\\":{\\\"selectedMenu\\\":\\\"settings\\\"}}\",appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1129067102240194561252/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",assets:[\"do_112835334818643968148\"],appId:\"dev.sunbird.portal\",contentEncoding:\"gzip\",artifactUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1129067102240194561252/artifact/1575527499196_do_1129067102240194561252.zip\",lockKey:\"b7992ea7-f326-40d0-abdd-1601146bca84\",contentType:\"Resource\",lastUpdatedBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",audience:[\"Student\"],visibility:\"Default\",consumerId:\"b3e90b00-1e9f-4692-9290-d014c20625f2\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"Ekstep\",version:2,pragma:[],prevState:\"Review\",license:\"CC BY 4.0\",lastPublishedOn:\"2019-12-05T06:31:39.415+0000\",size:74105,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"TEST-G-KP-2.0-001\",status:\"Live\",totalQuestions:0,code:\"org.sunbird.3qKh9v\",description:\"Test ECML Content\",streamingUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_1129067102240194561252-latest\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-12-05T06:09:10.490+0000\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-12-05T06:31:37.880+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-12-05T06:31:40.528+0000\",dialcodeRequired:\"No\",creator:\"Creation\",createdFor:[\"ORG_001\"],lastStatusChangedOn:\"2019-12-05T06:31:37.869+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",totalScore:0,pkgVersion:2,versionKey:\"1575527498230\",idealScreenDensity:\"hdpi\",s3Key:\"ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499420_do_1129067102240194561252_2.0.ecar\",lastSubmittedOn:\"2019-12-05T06:22:33.347+0000\",createdBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",compatibilityLevel:2,IL_UNIQUE_ID:\"do_1129067102240194561252\",resourceType:\"Learn\", subject: [\"Hindi\"], framework:\"NCF\"}] as row CREATE (n:domain) SET n += row");
        createRelationData();
        org.neo4j.graphdb.Result result = graphDb.execute("MATCH (n:domain{IL_UNIQUE_ID:'do_1129067102240194561252'}) match (m:domain{IL_UNIQUE_ID:'do_11232724509261824014'}) CREATE (m)-[r:associatedTo]->(n) return m,n,r");
        Map<String, Object> resultMap = (Map<String, Object>) result.next();
        org.neo4j.graphdb.Relationship relationship = (org.neo4j.graphdb.Relationship) resultMap.get("r");
        try (Transaction tx = graphDb.beginTx()) {
            Relation relation_3 = new Relation(graphId, relationship);
            tx.success();
        }
    }

    @Test(expected = ServerException.class)
    public void testRelationModel_2() {
        Relation relation_4 = new Relation(graphId, null);
    }

    @Test(expected = ServerException.class)
    public void testRelationModel_3() {
        Relation relation_5 = new Relation(graphId, null, null, null);
    }

    @Test
    public void testRelationModel_4() {
        Map<String, Object> testMap = new HashMap<>() {{
            put("name", "testNode");
        }};
        relation.setMetadata(testMap);
        relation.setEndNodeId("testNode");
        relation.setEndNodeMetadata(testMap);
        relation.setEndNodeName("testName");
        relation.setEndNodeObjectType("Content");
        relation.setEndNodeType("content");
        relation.setGraphId("domain");

        Assert.assertEquals("testNode", relation.getMetadata().get("name"));
        Assert.assertEquals("testNode", relation.getEndNodeId());
        Assert.assertEquals("testNode", relation.getEndNodeMetadata().get("name"));
        Assert.assertEquals("testName", relation.getEndNodeName());
        Assert.assertEquals("Content", relation.getEndNodeObjectType());
        Assert.assertEquals("content", relation.getEndNodeType());
        Assert.assertEquals("domain", relation.getGraphId());
    }

    @Test
    public void testRelationModel_5() {
        Map<String, Object> testMap = new HashMap<>() {{
            put("name", "testNode");
        }};
        relation.setId(1380914990L);
        relation.setStartNodeId("testNode");
        relation.setStartNodeMetadata(testMap);
        relation.setStartNodeName("testName");
        relation.setStartNodeObjectType("Content");
        relation.setStartNodeType("content");

        Assert.assertEquals("testNode", relation.getStartNodeId());
        Assert.assertEquals("testNode", relation.getStartNodeMetadata().get("name"));
        Assert.assertEquals("testName", relation.getStartNodeName());
        Assert.assertEquals("Content", relation.getStartNodeObjectType());
        Assert.assertEquals("content", relation.getStartNodeType());
        Assert.assertEquals(1380914990, relation.getId());
    }

    @Test
    public void testRelationModel_6() {
        Map<String, Object> testMap = new HashMap<>();
        relation.setMetadata(testMap);
        relation.setRelationType("testRel");
        Assert.assertEquals(0, relation.getMetadata().size());
        Assert.assertEquals("testRel", relation.getRelationType());
    }

    private static void createRelationData() {
        graphDb.execute("UNWIND [{identifier:\"Num:C3:SC2\",code:\"Num:C3:SC2\",keywords:[\"Subconcept\",\"Class 3\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",subject:\"numeracy\",channel:\"in.ekstep\",description:\"Multiplication\",versionKey:\"1484389136575\",gradeLevel:[\"Grade 3\",\"Grade 4\"],IL_FUNC_OBJECT_TYPE:\"Concept\",name:\"Multiplication\",lastUpdatedOn:\"2016-06-15T17:15:45.951+0000\",IL_UNIQUE_ID:\"Num:C3:SC2\",status:\"Live\"}, {code:\"31d521da-61de-4220-9277-21ca7ce8335c\",previewUrl:\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf\",downloadUrl:\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_11232724509261824014/untitled-content_1504790847410_do_11232724509261824014_2.0.ecar\",channel:\"in.ekstep\",language:[\"English\"],variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_11232724509261824014/untitled-content_1504790848197_do_11232724509261824014_2.0_spine.ecar\\\",\\\"size\\\":890.0}}\",mimeType:\"application/pdf\",streamingUrl:\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf\",idealScreenSize:\"normal\",createdOn:\"2017-09-07T13:24:20.720+0000\",contentDisposition:\"inline\",artifactUrl:\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf\",contentEncoding:\"identity\",lastUpdatedOn:\"2017-09-07T13:25:53.595+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2017-09-07T13:27:28.417+0000\",contentType:\"Resource\",lastUpdatedBy:\"Ekstep\",audience:[\"Student\"],visibility:\"Default\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",consumerId:\"e84015d2-a541-4c07-a53f-e31d4553312b\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"Ekstep\",pkgVersion:2,versionKey:\"1504790848417\",license:\"Creative Commons Attribution (CC BY)\",idealScreenDensity:\"hdpi\",s3Key:\"ecar_files/do_11232724509261824014/untitled-content_1504790847410_do_11232724509261824014_2.0.ecar\",size:4864851,lastPublishedOn:\"2017-09-07T13:27:27.410+0000\",createdBy:\"390\",compatibilityLevel:4,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"Untitled Content\",publisher:\"EkStep\",IL_UNIQUE_ID:\"do_11232724509261824014\",status:\"Live\",resourceType:[\"Study material\"]}] as row CREATE (n:domain) SET n += row");
    }
}
