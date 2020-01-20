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
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseHandler;
import org.sunbird.graph.health.HealthCheckManager;
import scala.concurrent.ExecutionContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
        HealthCheckManager.class
})
public class HealthActorTest {

    private static ActorSystem system = ActorSystem.create("system");
    private static final Props props = Props.create(HealthActor.class);

    @Test
    public void testSystemHealth() throws Exception {
        PowerMockito.mockStatic(HealthCheckManager.class);
        when(HealthCheckManager.checkAllSystemHealth(Mockito.any(ExecutionContext.class))).thenReturn(Futures.successful(getResponse()));
        Request request = new Request();
        TestKit probe = new TestKit(system);
        ActorRef actorRef = system.actorOf(props);
        actorRef.tell(request, probe.testActor());
        Response response = probe.expectMsgClass(Response.class);
        Assert.assertTrue("successful".equals(response.getParams().getStatus()));
        Assert.assertTrue(null != response.getResult().get("healthy"));
        Assert.assertTrue((Boolean) response.getResult().get("healthy"));
    }

    private Response getResponse() {
        Response response = ResponseHandler.OK();
        response.put("healthy", true);
        response.put("checks", generateChecks());
        return response;
    }


    private List<Map<String, Object>> generateChecks() {
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(generateCheck("graph db"));
        checks.add(generateCheck("cassandra db"));
        checks.add(generateCheck("redis cache"));
        return checks;
    }

    private Map<String, Object> generateCheck(String serviceName) {
        Map<String, Object> check = new HashMap<>();
        check.put("name", serviceName);
        check.put("healthy", true);
        return check;
    }
}
