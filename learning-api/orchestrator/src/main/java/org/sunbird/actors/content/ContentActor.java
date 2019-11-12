package org.sunbird.actors.content;

import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseHandler;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.nodes.DataNode;
import org.sunbird.utils.NodeUtils;
import scala.concurrent.Future;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class ContentActor extends BaseActor {
    public Future<Response> onReceive(Request request) throws Throwable {
        String operation = request.getOperation();
        if ("createContent".equals(operation)) {
            return create(request);
        } else if("updateContent".equals(operation)){
            return update(request);
        } else if("readContent".equals(operation)) {
            return read(request);
        }else {
            return ERROR(operation);
        }
    }

    private Future<Response> create(Request request) throws Exception {
        return DataNode.create(request, getContext().dispatcher())
                .map(new Mapper<Node, Response>() {
                    @Override
                    public Response apply(Node node) {
                        Response response = ResponseHandler.OK();
                        response.put("node_id", node.getIdentifier());
                        response.put("versionKey", node.getMetadata().get("versionKey"));
                        return response;
                    }
                }, getContext().dispatcher());
    }

    private Future<Response> update(Request request) throws Exception {
        return DataNode.update(request, getContext().dispatcher())
                .map(new Mapper<Node, Response>() {
                    @Override
                    public Response apply(Node node) {
                        Response response = ResponseHandler.OK();
                        response.put("node_id", node.getIdentifier());
                        response.put("identifier", node.getIdentifier());
                        response.put("versionKey", node.getMetadata().get("versionKey"));
                        return response;
                    }
                }, getContext().dispatcher());
    }
    private Future<Response> read(Request request) throws Exception {
        List<String> fields = Arrays.stream(((String) request.get("fields")).split(","))
                .filter(field -> StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null")).collect(Collectors.toList());
        request.getRequest().put("fields", fields);
        return DataNode.read(request, getContext().dispatcher())
                .map(new Mapper<Node, Response>() {
                    @Override
                    public Response apply(Node node) {
                        Map<String, Object> metadata = NodeUtils.serialize(node, fields);
                        Response response = ResponseHandler.OK();
                        response.put("content", metadata);
                        return response;
                    }
                }, getContext().dispatcher());
    }
}
