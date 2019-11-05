package org.sunbird.actor.core;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.dispatch.Futures;
import akka.pattern.Patterns;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseParams;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.MiddlewareException;
import org.sunbird.common.exception.ResourceNotFoundException;
import org.sunbird.common.exception.ResponseCode;
import org.sunbird.common.exception.ServerException;
import scala.concurrent.Future;

public abstract class BaseActor extends AbstractActor {

    public abstract Future<Response> onReceive(Request request) throws Throwable;

    private Future<Response> internalOnReceive(Request request) {
        try {
            return onReceive(request);
        } catch (Throwable e) {
            return ERROR(request.getOperation(), e);
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(Request.class, message -> {
            Patterns.pipe(internalOnReceive(message), getContext().dispatcher()).to(sender());
        }).build();
    }

    protected Future<Response> ERROR(String operation, Throwable exception) {
//        System.out.println("Exception in message processing for: " + operation + " :: message: " + exception.getMessage() + exception);
        return Futures.successful(getErrorResponse(exception));
    }

    public Future<Response> ERROR(String operation) {
        Response response = getErrorResponse(new ClientException(ResponseCode.CLIENT_ERROR.name(), "Invalid operation provided in request to process: " + operation));
        return Futures.successful(response);
    }

    private Response getErrorResponse(Throwable e) {
        e.printStackTrace();
        Response response = new Response();
        ResponseParams params = new ResponseParams();
        params.setStatus(ResponseParams.StatusType.failed.name());
        if (e instanceof MiddlewareException) {
            MiddlewareException mwException = (MiddlewareException) e;
            params.setErr(mwException.getErrCode());
            response.put("messages", mwException.getMessages());
        } else {
            params.setErr("ERR_SYSTEM_EXCEPTION");
        }
        System.out.println("Exception occurred in class :" + e.getClass().getName() + " with message :" + e.getMessage());
        params.setErrmsg(setErrMessage(e));
        response.setParams(params);
        setResponseCode(response, e);
        return response;
    }

    private String setErrMessage(Throwable e) {
        if (e instanceof MiddlewareException) {
            return e.getMessage();
        } else {
            return "Something went wrong in server while processing the request";
        }
    }

    private void setResponseCode(Response res, Throwable e) {
        if (e instanceof ClientException) {
            res.setResponseCode(ResponseCode.CLIENT_ERROR);
        } else if (e instanceof ServerException) {
            res.setResponseCode(ResponseCode.SERVER_ERROR);
        } else if (e instanceof ResourceNotFoundException) {
            res.setResponseCode(ResponseCode.RESOURCE_NOT_FOUND);
        } else {
            res.setResponseCode(ResponseCode.SERVER_ERROR);
        }
    }

    public void OK(Response response, ActorRef actor) {
        sender().tell(response, actor);
    }

}
