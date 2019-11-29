package org.sunbird.actors;

import akka.dispatch.Futures;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseHandler;
import scala.concurrent.Future;

public class HealthActor extends BaseActor {

    @Override
    public Future<Response> onReceive(Request request) throws Throwable {
        Response result = ResponseHandler.OK();
        result.put("healthy", true);
        return Futures.successful(result);
    }
}
