package org.sunbird.actors;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.testkit.TestKit;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.nodes.DataNode;
import scala.concurrent.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
        DataNode.class
})
public class ContentActorTest {
    private static ActorSystem system = ActorSystem.create("system");
    private static final Props props = Props.create(ContentActor.class);

    @Test
    public void unknownOperation() {
        Request request = new Request();
        request.setOperation("unknown");
        TestKit probe = new TestKit(system);
        ActorRef actorRef = system.actorOf(props);
        actorRef.tell(request, probe.testActor());
        Response response = probe.expectMsgClass(Response.class);
        Assert.assertTrue("failed".equals(response.getParams().getStatus()));
    }

    @Test
    public void readContent() throws Exception {
        PowerMockito.mockStatic(DataNode.class);
        String identifier = "do_1234";
        when(DataNode.read(Mockito.any(Request.class), Mockito.any(ExecutionContext.class))).thenReturn(Futures.successful(getNode(identifier)));
        Request request = getRequest("readContent");
        request.put("fields", "");
        request.setContext(new HashMap<String, Object>() {{
            put("schemaName", "content");
            put("version", "1.0");
            put("graph_id", "domain");
        }});

        TestKit probe = new TestKit(system);
        ActorRef actorRef = system.actorOf(props);
        actorRef.tell(request, probe.testActor());
        Response response = probe.expectMsgClass(Response.class);
        Assert.assertTrue("successful".equals(response.getParams().getStatus()));
        Assert.assertTrue(null != response.getResult().get("content"));
        Map<String, Object> content = (Map<String, Object>) response.getResult().get("content");
        Assert.assertTrue(identifier.equals(content.get("identifier")));

    }

    private Request getRequest(String operation) {
        Request request = new Request();
        request.setContext(new HashMap<String, Object>() {{
            put("schemaName", "content");
        }});
        request.setOperation(operation);
        return request;
    }

    private Node getNode(String identifier) {
        Node node = new Node();
        node.setGraphId("domain");
        node.setIdentifier(identifier);
        node.setMetadata(new HashMap<>());
        return node;
    }
}
