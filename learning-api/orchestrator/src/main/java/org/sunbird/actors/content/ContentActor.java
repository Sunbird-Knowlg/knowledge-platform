package org.sunbird.actors.content;

import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.common.ContentParams;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseHandler;

import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ErrorCodes;
import org.sunbird.common.exception.ResponseCode;

import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.nodes.DataNode;
import org.sunbird.utils.NodeUtils;
import scala.concurrent.Future;

import javax.xml.crypto.Data;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ContentActor extends BaseActor {

    public Future<Response> onReceive(Request request) throws Throwable {
        String operation = request.getOperation();
        switch(operation) {
            case "createContent": return create(request);
            case "readContent": return read(request);
            case "updateContent": return update(request);
            case "addHierarchy":
            case "removeHierarchy": return partialHierarchy(request);
            default: return ERROR(operation);
        }
    }

    private Future<Response> create(Request request) throws Exception {
        populateDefaultersForCreation(request);
        return DataNode.create(request, getContext().dispatcher())
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
                        if (NodeUtils.isRetired(node))
                            return ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "Content not found with identifier: " + node.getIdentifier());
                        Map<String, Object> metadata = NodeUtils.serialize(node, fields);
                        Response response = ResponseHandler.OK();
                        response.put("content", metadata);
                        return response;
                    }
                }, getContext().dispatcher());
    }

    private static void populateDefaultersForCreation(Request request) {
        setDefaultsBasedOnMimeType(request, ContentParams.create.name());
        validateLicense(request);
    }

    private static void validateLicense(Request request) {
        //TODO: for license validation
    }

    private static void setDefaultsBasedOnMimeType(Request request, String operation) {

        String mimeType = (String) request.get(ContentParams.mimeType.name());
        if (StringUtils.isNotBlank(mimeType) && operation.equalsIgnoreCase(ContentParams.create.name())) {
            if (StringUtils.equalsIgnoreCase("application/vnd.ekstep.plugin-archive", mimeType)) {
                String code = (String) request.get(ContentParams.code.name());
                if (null == code || StringUtils.isBlank(code))
                    throw new ClientException("ERR_PLUGIN_CODE_REQUIRED", "Unique code is mandatory for plugins");
                request.put(ContentParams.identifier.name(), request.get(ContentParams.code.name()));
            } else {
                request.put(ContentParams.osId.name(), "org.ekstep.quiz.app");
            }

            if (mimeType.endsWith("archive") || mimeType.endsWith("vnd.ekstep.content-collection")
                    || mimeType.endsWith("epub"))
                request.put(ContentParams.contentEncoding.name(), ContentParams.gzip.name());
            else
                request.put(ContentParams.contentEncoding.name(), ContentParams.identity.name());

            if (mimeType.endsWith("youtube") || mimeType.endsWith("x-url"))
                request.put(ContentParams.contentDisposition.name(), ContentParams.online.name());
            else
                request.put(ContentParams.contentDisposition.name(), ContentParams.inline.name());
        }
    }

    private Future<Response> partialHierarchy(Request request) {
        String rootId = (String) request.get("rootId");
        String unitId = (String) request.get("unitId");
        List<String> leafNodes = (List<String>) request.get("leafNodes");

        if (StringUtils.isEmpty(rootId))
            throw new ClientException("ERR_BAD_REQUEST", "rootId is mandatory");
        if (StringUtils.isEmpty(rootId))
            throw new ClientException("ERR_BAD_REQUEST", "clientId is mandatory");
        if (CollectionUtils.isEmpty(leafNodes))
            throw new ClientException("ERR_BAD_REQUEST", "leafNodes are mandatory");

        String operation = request.getOperation();
        if (StringUtils.equalsIgnoreCase(operation, "addHierarchy")) {
            Response response = ResponseHandler.OK();
            response.put("identifier", rootId);
            response.put(unitId, leafNodes);
            return Futures.successful(response);
        } else {
            Response response = ResponseHandler.OK();
            response.put("identifier", rootId);
            return Futures.successful(response);
        }
    }

/*

        return DataNode.partialHierarchy(request, getContext().dispatcher())
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
    }*/
}
