package org.sunbird.actors;

import akka.dispatch.Mapper;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.cache.util.RedisCacheUtil;
import org.sunbird.common.ContentParams;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseHandler;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ResponseCode;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.nodes.DataNode;
import org.sunbird.utils.NodeUtils;
import org.sunbird.utils.RequestUtils;
import scala.concurrent.Future;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ContentActor extends BaseActor {

    private static final String SCHEMA_NAME = "content";

    public Future<Response> onReceive(Request request) throws Throwable {
        String operation = request.getOperation();
        switch(operation) {
            case "createContent": return create(request);
            case "readContent": return read(request);
            case "updateContent": return update(request);
            default: return ERROR(operation);
        }
    }

    private Future<Response> create(Request request) throws Exception {
        populateDefaultersForCreation(request);
        request.getContext().put("schemaName", SCHEMA_NAME);
        RequestUtils.restrictProperties(request);
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
        populateDefaultersForUpdation(request);
        request.getContext().put("schemaName", SCHEMA_NAME);
        RequestUtils.restrictProperties(request);
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
        request.getContext().put("schemaName", SCHEMA_NAME);
        List<String> fields = Arrays.stream(((String) request.get("fields")).split(","))
                .filter(field -> StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null")).collect(Collectors.toList());
        request.getRequest().put("fields", fields);
        return DataNode.read(request, getContext().dispatcher())
                .map(new Mapper<Node, Response>() {
                    @Override
                    public Response apply(Node node) {
                        if (NodeUtils.isRetired(node))
                            return ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "Content not found with identifier: " + node.getIdentifier());
                        Map<String, Object> metadata = NodeUtils.serialize(node, fields, SCHEMA_NAME);
                        Response response = ResponseHandler.OK();
                        response.put("content", metadata);
                        return response;
                    }
                }, getContext().dispatcher());
    }

    private static void populateDefaultersForCreation(Request request) {
        setDefaultsBasedOnMimeType(request, ContentParams.create.name());
        setDefaultLicense(request);
    }

    private static void populateDefaultersForUpdation(Request request){
        if(request.getRequest().containsKey(ContentParams.body.name()))
            request.put(ContentParams.artifactUrl.name(), null);
    }

    private static void setDefaultLicense(Request request) {
        if(StringUtils.isEmpty((String)request.getRequest().get("license"))){
            String defaultLicense = RedisCacheUtil.getString("channel_" + (String)request.getRequest().get("channel") + "_license");
            if(StringUtils.isNotEmpty(defaultLicense))
                request.getRequest().put("license", defaultLicense);
            else
                System.out.println("Default License is not available for channel: " + (String)request.getRequest().get("channel"));
        }
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

}
