package org.sunbird.actors;

import org.sunbird.actor.core.BaseActor;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.graph.health.HealthCheckManager;

import scala.concurrent.Future;

public class HealthActor extends BaseActor {

    @Override
    public Future<Response> onReceive(Request request) {
        return HealthCheckManager.checkAllSystemHealth(getContext().dispatcher());
    }

}
