package org.sunbird.graph.service.util;

import org.apache.commons.lang3.StringUtils;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.sunbird.common.Platform;
import org.sunbird.graph.service.common.DACConfigurationConstants;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.common.GraphOperation;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DriverUtil {

	private static Map<String, Driver> driverMap = new HashMap<String, Driver>();

	public static Driver getDriver(String graphId, GraphOperation graphOperation) {
		TelemetryManager.log("Get Driver for Graph Id: "+ graphId);
		String driverKey = graphId + DACConfigurationConstants.UNDERSCORE
				+ StringUtils.lowerCase(graphOperation.name());
		TelemetryManager.log("Driver Configuration Key: " + driverKey);

		Driver driver = driverMap.get(driverKey);
		if (null == driver) {
			driver = loadDriver(graphId, graphOperation);
			driverMap.put(driverKey, driver);
		}
		return driver;
	}

	public static Driver loadDriver(String graphId, GraphOperation graphOperation) {
		TelemetryManager.log("Loading driver for Graph Id: "+ graphId);
		String route = getRoute(graphId, graphOperation);
		Driver driver = GraphDatabase.driver(route, getConfig());
		if (null != driver)
			registerShutdownHook(driver);
		return driver;
	}

	public static Config getConfig() {
		Config.ConfigBuilder config = Config.build();
		config.withEncryptionLevel(Config.EncryptionLevel.NONE);
		config.withMaxIdleSessions(DACConfigurationConstants.NEO4J_SERVER_MAX_IDLE_SESSION);
		config.withTrustStrategy(Config.TrustStrategy.trustAllCertificates());
		return config.toConfig();
	}

	public static void closeDrivers() {
		for (Iterator<Map.Entry<String, Driver>> it = driverMap.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, Driver> entry = it.next();
			Driver driver = entry.getValue();
			driver.close();
			it.remove();
		}
	}

	private static void registerShutdownHook(Driver driver) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				TelemetryManager.log("Closing Neo4j Graph Driver...");
				if (null != driver)
					driver.close();
			}
		});
	}

	public static String getRoute(String graphId, GraphOperation graphOperation) {
		// Checking Graph Id for 'null' or 'Empty'
		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Graph Id: " + graphId + "]");

		String routeUrl = "bolt://localhost:7687";
		String baseKey = DACConfigurationConstants.DEFAULT_ROUTE_PROP_PREFIX + StringUtils.lowerCase(graphOperation.name())
				+ DACConfigurationConstants.DOT;
		if (Platform.config.hasPath(baseKey + graphId)) {
			routeUrl = Platform.config.getString(baseKey + graphId);
		} else if (Platform.config.hasPath(baseKey + DACConfigurationConstants.DEFAULT_NEO4J_BOLT_ROUTE_ID)) {
			routeUrl = Platform.config.getString(baseKey + DACConfigurationConstants.DEFAULT_NEO4J_BOLT_ROUTE_ID);
		} else {
			TelemetryManager.warn("Graph connection configuration not defined.");
		}
		TelemetryManager.log("Request path for graph: " + graphId + " | URL: " + routeUrl);
		return routeUrl;
	}

}
