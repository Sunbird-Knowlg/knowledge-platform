package org.sunbird.actors;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestKit;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;

@RunWith(PowerMockRunner.class)
public class HealthActorTest {

    private static ActorSystem system = ActorSystem.create("system");
    private static final Props props = Props.create(HealthActor.class);

    @Test
    public void testSystemHealth() {
        Request request = new Request();
        TestKit probe = new TestKit(system);
        ActorRef actorRef = system.actorOf(props);
        actorRef.tell(request, probe.testActor());
        Response response = probe.expectMsgClass(Response.class);
        boolean health = (boolean) response.get("healthy");
        Assert.assertTrue(health);
    }
}
