package org.sunbird.actors;

import akka.dispatch.Mapper;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseHandler;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.nodes.DataNode;
import org.sunbird.utils.ItemSetOperations;
import org.sunbird.utils.NodeUtils;
import scala.concurrent.Future;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ItemSetActor extends BaseActor {
	@Override
	public Future<Response> onReceive(Request request) throws Throwable {
		String operation = request.getOperation();
		if (ItemSetOperations.createItemSet.name().equals(operation)) {
			return create(request);
		} else if (ItemSetOperations.readItemSet.name().equals(operation)) {
			return read(request);
		} else if (ItemSetOperations.updateItemSet.name().equals(operation)) {
			return update(request);
		} else if (ItemSetOperations.retireItemSet.name().equals(operation)) {
			return retire(request);
		}else if (ItemSetOperations.reviewItemSet.name().equals(operation)) {
			return review(request);
		} else {
			return ERROR(operation);
		}
	}

	private Future<Response> create(Request request) throws Exception {
		return DataNode.create(request, getContext().dispatcher())
				.map(new Mapper<Node, Response>() {
					@Override
					public Response apply(Node node) {
						Response response = ResponseHandler.OK();
						response.put("identifier", node.getIdentifier());
						return response;
					}
				}, getContext().dispatcher());
	}

	private Future<Response> read(Request request) throws Exception {
		List<String> fields = Arrays.stream(((String) request.get("fields")).split(","))
				.filter(field -> StringUtils.isNotBlank(field)).collect(Collectors.toList());
		request.getRequest().put("fields", fields);
		return DataNode.read(request, getContext().dispatcher())
				.map(new Mapper<Node, Response>() {
					@Override
					public Response apply(Node node) {
						Map<String, Object> metadata = NodeUtils.serialize(node, fields, (String) request.getContext().get("schemaName"), (String) request.getContext().get("version"));
						metadata.remove("versionKey");
						Response response = ResponseHandler.OK();
						response.put("itemset", metadata);
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
						String identifier = node.getIdentifier();
						response.put("identifier", identifier);
						return response;
					}
				}, getContext().dispatcher());
	}

	private Future<Response> review(Request request) throws Exception {
		//TODO: Read the node and filter relationship
		request.put("status","Review");
		return DataNode.update(request, getContext().dispatcher())
				.map(new Mapper<Node, Response>() {
					@Override
					public Response apply(Node node) {
						Response response = ResponseHandler.OK();
						String identifier = node.getIdentifier();
						response.put("identifier", identifier);
						return response;
					}
				}, getContext().dispatcher());
	}

	private Future<Response> retire(Request request) throws Exception {
		request.put("status","Retired");
		return DataNode.update(request, getContext().dispatcher())
				.map(new Mapper<Node, Response>() {
					@Override
					public Response apply(Node node) {
						Response response = ResponseHandler.OK();
						String identifier = node.getIdentifier();
						response.put("identifier", identifier);
						return response;
					}
				}, getContext().dispatcher());
	}


}
