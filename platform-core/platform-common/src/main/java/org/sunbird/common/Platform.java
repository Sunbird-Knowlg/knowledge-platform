package org.sunbird.common;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author Mahesh Kumar Gangula
 *
 */

public class Platform {
	private static Config defaultConf = ConfigFactory.load();
	private static Config envConf = ConfigFactory.systemEnvironment();
	public static Config config = envConf.withFallback(defaultConf);

	private static int requestTimeout = 30;
	private static Map<String, List<String>> graphIds = new HashMap<>();

	
	public static void loadProperties(Config conf) {
		config = config.withFallback(conf);
	}

	public static int getTimeout() {
		return requestTimeout;
	}

	public static List<String> getGraphIds(String... services) {
		List<String> ids = new ArrayList<>();
		for (String service: services) {
			ids.addAll(getGraphIds(service));
		}
		return ids;
	}
	
	private static List<String> getGraphIds(String service) {
		service = service.toLowerCase();
		if (!graphIds.containsKey(service)) {
			String key = service + ".graph_ids";
			if (config.hasPath(key)) {
				graphIds.put(service, config.getStringList(key));
			} else
				return Arrays.asList();
		}
		return graphIds.get(service);
	}

	public static String getString(String key, String defaultVal) {
		return config.hasPath(key) ? config.getString(key) : defaultVal;
	}

	public static Integer getInteger(String key, Integer defaultVal) {
		return config.hasPath(key) ? config.getInt(key) : defaultVal;
	}

	public static List<String> getStringList(String key, List<String> defaultVal) {
		return config.hasPath(key) ? config.getStringList(key) : defaultVal;
	}

}
