package org.sunbird.actors;

import akka.dispatch.Mapper;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.core.BaseActor;

import org.sunbird.common.Slug;

import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseHandler;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ResponseCode;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.nodes.DataNode;
import org.sunbird.utils.NodeUtils;
import scala.concurrent.Future;
import org.sunbird.utils.LicenseOperations;
import org.sunbird.utils.RequestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LicenseActor extends BaseActor {

    public Future<Response> onReceive(Request request) throws Throwable {
        String operation = request.getOperation();
        if (LicenseOperations.createLicense.name().equals(operation)) {
            return create(request);
        } else if (LicenseOperations.readLicense.name().equals(operation)) {
            return read(request);
        } else if (LicenseOperations.updateLicense.name().equals(operation)) {
            return update(request);
        } else if (LicenseOperations.retireLicense.name().equals(operation)) {
            return retire(request);
        } else {
            return ERROR(operation);

        }
    }

    private Future<Response> create(Request request) throws Exception {
        RequestUtils.restrictProperties(request);
        if (request.getRequest().containsKey("identifier")) {
            throw new ClientException("ERR_NAME_SET_AS_IDENTIFIER", "name will be set as identifier");
        }
        if (request.getRequest().containsKey("name")) {
            request.getRequest().put("identifier", Slug.makeSlug((String) request.getRequest().get("name")));
        }
        return DataNode.create(request, getContext().dispatcher())
                .map(new Mapper<Node, Response>() {
                    @Override
                    public Response apply(Node node) {
                        Response response = ResponseHandler.OK();
                        response.put("node_id", node.getIdentifier());
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
                        if(NodeUtils.isRetired(node))
                           return ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "License not found with identifier: " + node.getIdentifier());
                        Map<String, Object> metadata = NodeUtils.serialize(node, fields, (String) request.getContext().get("schemaName"));
                        Response response = ResponseHandler.OK();
                        response.put("license", metadata);
                        return response;
                    }
                }, getContext().dispatcher());
    }

    private Future<Response> update(Request request) throws Exception {
        RequestUtils.restrictProperties(request);
        request.getRequest().put("status", "Live");
        return DataNode.update(request, getContext().dispatcher())
                .map(new Mapper<Node, Response>() {
                    @Override
                    public Response apply(Node node) {
                        Response response = ResponseHandler.OK();
                        response.put("node_id", node.getIdentifier());
                        return response;
                    }
                }, getContext().dispatcher());
    }
    private Future<Response> retire(Request request) throws Exception {
        request.getRequest().put("status", "Retired");
        return DataNode.update(request, getContext().dispatcher())
                .map(new Mapper<Node, Response>() {
                    @Override
                    public Response apply(Node node) {
                        Response response = ResponseHandler.OK();
                        response.put("node_id", node.getIdentifier());
                        return response;
                    }
                }, getContext().dispatcher());
    }
}
