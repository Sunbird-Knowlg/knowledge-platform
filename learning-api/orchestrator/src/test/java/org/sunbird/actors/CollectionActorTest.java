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
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseHandler;
import org.sunbird.managers.HierarchyManager;
import scala.concurrent.ExecutionContext;
import java.util.*;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
        HierarchyManager.class
})
@PowerMockIgnore({"javax.management.*", "sun.security.ssl.*", "javax.net.ssl.*" , "javax.crypto.*"})
public class CollectionActorTest {
    private static ActorSystem system = ActorSystem.create("system");
    private static final Props props = Props.create(CollectionActor.class);

    @Test
    public void getHierarchy() throws Exception {
        PowerMockito.mockStatic(HierarchyManager.class);
        String identifier = "do_1234";
        when(HierarchyManager.getHierarchy(Mockito.any(Request.class), Mockito.any(ExecutionContext.class))).thenReturn(Futures.successful(fetchHierachyResponse(identifier)));
        Request request = getRequest("getHierarchy");
        request.getContext().put("identifier", identifier);
        request.getContext().put("mode", "edit");
        request.setContext(new HashMap<String, Object>() {{
            put("schemaName", "collection");
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
            put("schemaName", "collection");
        }});
        request.setOperation(operation);
        return request;
    }

    private Response fetchHierachyResponse(String identifier) {
        Response response = ResponseHandler.OK();
        response.putAll(new HashMap<String, Object>(){{
            put("content", new HashMap<String, Object>(){{
                put("identifier",identifier);
                put("status","Live");
                put("depth",0);
                put("parent",identifier);
                put("children",new HashMap<String, Object>(){{
                    put("identifier","do_123");
                    put("depth",1);
                    put("parent",identifier);
                    put("visibility","Parent");
                }});
            }});
        }});
        return response;
    }

}
