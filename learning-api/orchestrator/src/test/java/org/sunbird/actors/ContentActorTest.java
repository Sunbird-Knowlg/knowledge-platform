package org.sunbird.actors;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestKit;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.graph.nodes.DataNode;

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

//    @Test
//    public void createContent() {
//        PowerMockito.mockStatic(DataNode.class);
//    }
}
