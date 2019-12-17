package org.sunbird.actors;

import akka.dispatch.Futures;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseHandler;
import org.sunbird.utils.ItemSetOperations;
import scala.concurrent.Future;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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
		} else {
			return ERROR(operation);
		}
	}

	private Future<Response> create(Request request) throws Exception {
		Response response = ResponseHandler.OK();
		response.put("identifier", "do_1129152191260999681109");
		response.put("versionKey", "123456");
		return Futures.successful(response);
	}

	private Future<Response> read(Request request) throws Exception {
		Response response = ResponseHandler.OK();
		Map<String, Object> itemset = new HashMap<String, Object>(){{
			put("identifier", "do_1129152191260999681109");
			put("versionKey","123456");
			put("title", "Test Item Set");
			put("description", "Test Item Set");
			put("language", Arrays.asList("English"));
			put("max_score", 10);
			put("type", "materialised");
			put("owner", "KP");
			put("difficulty_level","easy");
			put("purpose","assessment");
			put("sub_purpose","assessment");
			put("depth_of_knowledge","");
			put("used_for","sunbird");
			put("copyright","sunbird");
			put("createdBy","sunbird");
			put("assessmentItem",new ArrayList<Map<String, Object>>(){{
				add(new HashMap<String, Object>(){{
					put("identifier","do_2222222222");
				}});
			}});

		}};
		response.putAll(itemset);
		return Futures.successful(response);
	}

	private Future<Response> update(Request request) throws Exception {
		Response response = ResponseHandler.OK();
		response.put("identifier", "do_1129152191260999681109");
		response.put("versionKey", "1005678");
		return Futures.successful(response);
	}

	private Future<Response> retire(Request request) throws Exception {
		Response response = ResponseHandler.OK();
		response.put("identifier", "do_1129152191260999681109");
		response.put("versionKey", "5001005291");
		return Futures.successful(response);
	}


}
