package org.sunbird.actors;

import org.sunbird.actor.core.BaseActor;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.managers.HierarchyManager;
import org.sunbird.managers.UpdateHierarchyManager;
import scala.concurrent.Future;

public class CollectionActor extends BaseActor {

    private static final String SCHEMA_NAME = "collection";
    
    @Override
    public Future<Response> onReceive(Request request) throws Throwable {
        String operation = request.getOperation();
        switch (operation) {
            case "addHierarchy": return addLeafNodesToHierarchy(request);
            case "removeHierarchy": return removeLeafNodesFromHierarchy(request);
            case "updateHierarchy": return updateHierarchy(request);
            case "getHierarchy": return getHierarchy(request);
            default: return ERROR(operation);
        }
    }

    private Future<Response> addLeafNodesToHierarchy(Request request) throws Exception {
        request.getContext().put("schemaName", SCHEMA_NAME);
        return HierarchyManager.addLeafNodesToHierarchy(request, getContext().dispatcher());
    }

    private Future<Response> removeLeafNodesFromHierarchy(Request request) throws Exception {
        request.getContext().put("schemaName", SCHEMA_NAME);
        return HierarchyManager.removeLeafNodesFromHierarchy(request, getContext().dispatcher());
    }

    private Future<Response> updateHierarchy(Request request) throws Exception {
        request.getContext().put("schemaName", SCHEMA_NAME);
        return UpdateHierarchyManager.updateHierarchy(request, getContext().dispatcher());
    }
    private Future<Response> getHierarchy(Request request) throws Exception {
        request.getContext().put("schemaName", SCHEMA_NAME);
        return HierarchyManager.getHierarchy(request, getContext().dispatcher());
    }
}
