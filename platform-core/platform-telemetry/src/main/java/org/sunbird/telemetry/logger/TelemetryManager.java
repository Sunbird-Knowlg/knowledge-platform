package org.sunbird.telemetry.logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.MiddlewareException;
import org.sunbird.common.exception.ResponseCode;
import org.sunbird.telemetry.TelemetryGenerator;
import org.sunbird.telemetry.TelemetryParams;
import org.sunbird.telemetry.handler.Level;
import org.sunbird.telemetry.handler.TelemetryHandler;
import org.sunbird.telemetry.handler.TelemetryLoggingHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is used to generate and handle telemetry. 
 * 
 * @author Rashmi & Mahesh
 *
 */
public class TelemetryManager {

	/**
	 * 
	 */
	private static TelemetryHandler telemetryHandler = new TelemetryLoggingHandler();

	private static final String DEFAULT_CHANNEL_ID = Platform.config.hasPath("channel.default") ? Platform.config.getString("channel.default") : "in.ekstep";

	/**
	 * To log api_access as a telemetry event.
	 * 
	 * @param context
	 * @param params
	 */

	public static void access(Map<String, String> context, Map<String, Object> params) {
		String event = TelemetryGenerator.access(context, params);
		telemetryHandler.send(event, Level.INFO, true);
	}

	/**
	 * To log only message as a telemetry event.
	 * 
	 * @param message
	 */
	public static void log(String message) {
		log(message, null, Level.DEBUG.name());
	}

	/**
	 * To log message with params as a telemetry event.
	 * 
	 * @param message
	 */
	public static void log(String message, Map<String, Object> params) {
		log(message, params, Level.DEBUG.name());
	}
	
	/**
	 * To log only message as a telemetry event.
	 * 
	 * @param message
	 */
	public static void info(String message) {
		log(message, null, Level.INFO.name());
	}

	/**
	 * To log message with params as a telemetry event.
	 * 
	 * @param message
	 */
	public static void info(String message, Map<String, Object> params) {
		log(message, params, Level.INFO.name());
	}
	
	/**
	 * 
	 * @param message
	 */
	
	public static void warn(String message) {
		log(message, null, Level.WARN.name());
	}
	
	/**
	 * 
	 * @param message
	 * @param params
	 */
	
	public static void warn(String message, Map<String, Object> params) {
		log(message, params, Level.WARN.name());
	}
	
	/**
	 * 
	 * @param message
	 */
	public static void error(String message) {
		log(message, null, Level.ERROR.name());
	}
	
	/**
	 * 
	 * @param message
	 * @param params
	 */
	public static void error(String message, Map<String, Object> params) {
		log(message, params, Level.ERROR.name());
	}

	/**
	 * To log exception with message and params as a telemetry event.
	 * 
	 * @param message
	 * @param e
	 */
	public static void error(String message, Throwable e) {
		error(message, e, null);
	}
	
	/**
	 * 
	 * @param message
	 * @param e
	 * @param object
	 */
	public static void error(String message, Throwable e, Object object) {
		Map<String, String> context = getContext();
		String stacktrace = ExceptionUtils.getStackTrace(e);
		String code = ResponseCode.SERVER_ERROR.name();
		if (e instanceof MiddlewareException) {
			code = ((MiddlewareException) e).getErrCode();
		}
		String event = TelemetryGenerator.error(context, code, "system", stacktrace);
		telemetryHandler.send(event, Level.ERROR);
	}
	
	
	public static void audit(String id, String type, List<String> props) {
		audit(id, type, props, null, null);
	}
	
	public static void audit(String id, String type, List<String> props, String state, String prevState) {
		Map<String, String> context = getContext();
		context.put("objectId", id);
		context.put("objectType", type);
		String event = TelemetryGenerator.audit(context, props, state, prevState);
		telemetryHandler.send(event, Level.INFO);
	}

	/**
	 * @param query
	 * @param filters
	 * @param sort
	 * @param size
	 * @param topN
	 * @param type
	 */
	public static void search(String query, Object filters, Object sort, int size, Object topN,
			String type) {
		search(null, query, filters, sort, size, topN, type);
	}

	/**
	 * @param context
	 * @param query
	 * @param filters
	 * @param sort
	 * @param size
	 * @param topN
	 * @param type
	 */
	public static void search(Map<String, Object> context, String query, Object filters, Object sort,
							  int size, Object topN, String type) {
		Map<String, String> reqContext=null;
		String deviceId=null;
		String appId=null;

		if(null!=context) {
			reqContext=new HashMap<String,String>();
			reqContext.put(TelemetryParams.ACTOR.name(),(String) context.get(TelemetryParams.ACTOR.name()));
			reqContext.put(TelemetryParams.ENV.name(),(String) context.get(TelemetryParams.ENV.name()));
			reqContext.put(TelemetryParams.CHANNEL.name(),(String) context.get("CHANNEL_ID"));
			reqContext.put(TelemetryParams.APP_ID.name(),(String) context.get("APP_ID"));
			deviceId = (String) context.get("DEVICE_ID");
			if(StringUtils.isNotBlank(deviceId))
				reqContext.put("did", deviceId);
			if (null != context.get("objectId") && null != context.get("objectType")) {
				reqContext.put("objectId", (String) context.get("objectId"));
				reqContext.put("objectType", (String) context.get("objectType"));
			}
		}

		String event = TelemetryGenerator.search(reqContext, query, filters, sort, null, size, topN, type);
		telemetryHandler.send(event, Level.INFO, true);
	}

	/**
	 * To log exception with message and params for user specified log level as a
	 * telemetry event.
	 * @param message
	 * @param params
	 * @param logLevel
	 */
	private static void log(String message, Map<String, Object> params, String logLevel) {
		Map<String, String> context = getContext();
		String event = TelemetryGenerator.log(context, "system", logLevel, message, null, params);
		telemetryHandler.send(event, Level.getLevel(logLevel));
	}

	private static Map<String, String> getContext() {
		Map<String, String> context = new HashMap<String, String>();
		context.put(TelemetryParams.ACTOR.name(), "org.sunbird.learning.platform");
		context.put(TelemetryParams.CHANNEL.name(), getContextValue("CHANNEL_ID", DEFAULT_CHANNEL_ID));
		context.put(TelemetryParams.ENV.name(), getContextValue(TelemetryParams.ENV.name(), "system"));
		return context;
	}

	private static String getContextValue(String key, String defaultValue) {
		// TODO: refactor this.
		return defaultValue;
	}
}
