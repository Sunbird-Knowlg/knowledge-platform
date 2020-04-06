package org.sunbird.actors;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestKit;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.exception.ResponseCode;
import org.sunbird.search.client.ElasticSearchUtil;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

@RunWith(PowerMockRunner.class)
@PrepareForTest({HealthActor.class, ElasticSearchUtil.class})
@PowerMockIgnore({"javax.management.*", "sun.security.ssl.*", "javax.net.ssl.*" , "javax.crypto.*"})
public class HealthActorTest extends SearchBaseActorTest{

    @AfterClass
    public static void afterTest() throws Exception {
        TestKit.shutdownActorSystem(system, Duration.create(2, TimeUnit.SECONDS), true);
        system = null;
    }
    
    @Test
    public void testHealthSuccess(){
        Request request = new Request();
        request.setOperation("health");
        PowerMockito.mockStatic(ElasticSearchUtil.class);
        PowerMockito.when(ElasticSearchUtil.isIndexExists(Mockito.anyString())).thenReturn(true);
        Response resp = getResponse(request, Props.create(HealthActor.class));
        Assert.assertEquals(ResponseCode.OK, resp.getResponseCode());
    }

    @Test
    public void testHealthFail(){
        Request request = new Request();
        request.setOperation("health");
        PowerMockito.mockStatic(ElasticSearchUtil.class);
        PowerMockito.when(ElasticSearchUtil.isIndexExists(Mockito.anyString())).thenReturn(false);
        Response resp = getResponse(request, Props.create(HealthActor.class));
        Assert.assertEquals(ResponseCode.SERVER_ERROR, resp.getResponseCode());
    }
}
