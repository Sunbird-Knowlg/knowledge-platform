package org.sunbird.utils;

import org.sunbird.common.dto.Request;
import org.sunbird.common.exception.ClientException;
import org.sunbird.graph.schema.DefinitionNode;
import scala.collection.JavaConversions;

import java.util.List;

public class RequestUtils {

    /**
     * Method to restrict invalid properties sent in the request
     * @param request
     */
    public static void restrictProperties(Request request) {
        String graphId = (String) request.getContext().get("graph_id");
        String version = (String) request.getContext().get("version");
        String objectType = (String) request.getContext().get("objectType");
        String schemaName = (String) request.getContext().get("schemaName");
        String operation = request.getOperation().toLowerCase().replace(objectType.toLowerCase(), "");
        List<String> restrictedProps = JavaConversions.seqAsJavaList(DefinitionNode.getRestrictedProperties(graphId, version, operation, schemaName));
        if (restrictedProps.stream().anyMatch(prop -> request.getRequest().containsKey(prop)))
            throw new ClientException("ERROR_RESTRICTED_PROP", "Properties in list " + restrictedProps + " are not allowed in request");
    }

}
