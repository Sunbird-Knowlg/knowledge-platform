package org.sunbird.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Session;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ServerException;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CassandraConnector {

	/** Cassandra Session. */
	private static Session session;

	static {
		if (Platform.getBoolean("service.db.cassandra.enabled", true))
			prepareSession(getConsistencyLevel());
	}

	/**
	 * Provide Cassandra Session.
	 *
	 * @return session.
	 */
	public static Session getSession() {
		if (null == session || session.isClosed()) {
			ConsistencyLevel level = getConsistencyLevel();
			prepareSession(level);
		}
		if (null == session)
			throw new ServerException("ERR_INITIALISE_CASSANDRA_SESSION", "Error while initialising cassandra");
		return session;
	}

	/**
	 *
	 * @param level
	 */
	private static void prepareSession(ConsistencyLevel level) {
		List<String> connectionInfo = getConnectionInfo();
		List<InetSocketAddress> addressList = getSocketAddress(connectionInfo);
		try {
			if (null != level) {
				session = Cluster.builder()
						.addContactPointsWithPorts(addressList)
						.withQueryOptions(new QueryOptions().setConsistencyLevel(level))
						.withoutJMXReporting()
						.withProtocolVersion(ProtocolVersion.V4)
						.build().connect();
			} else {
				session = Cluster.builder()
						.addContactPointsWithPorts(addressList)
						.withoutJMXReporting()
						.withProtocolVersion(ProtocolVersion.V4)
						.build().connect();
			}

			registerShutdownHook();
		} catch (Exception e) {
			e.printStackTrace();
			TelemetryManager.error("Error! While Loading Cassandra Properties." + e.getMessage(), e);
		}
	}

	/**
	 *
	 * @return
	 */
	private static List<String> getConnectionInfo() {
		List<String> connectionInfo = null;
		if (Platform.config.hasPath("cassandra.connection"))
			connectionInfo = Arrays.asList(Platform.config.getString("cassandra.connection").split(","));
		if (null == connectionInfo || connectionInfo.isEmpty())
			connectionInfo = new ArrayList<>(Arrays.asList("localhost:9042"));

		return connectionInfo;
	}

	/**
	 *
	 * @param hosts
	 * @return
	 */
	private static List<InetSocketAddress> getSocketAddress(List<String> hosts) {
		List<InetSocketAddress> connectionList = new ArrayList<>();
		for (String connection : hosts) {
			String[] conn = connection.split(":");
			String host = conn[0];
			int port = Integer.valueOf(conn[1]);
			connectionList.add(new InetSocketAddress(host, port));
		}
		return connectionList;
	}

	/**
	 * Close connection with the cluster.
	 *
	 */
	public static void close() {
		if (null != session && !session.isClosed())
			session.close();
		session = null;
	}

	/**
	 * Register JVM shutdown hook to close cassandra open session.
	 */
	private static void registerShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				TelemetryManager.log("Shutting down Cassandra connector session");
				CassandraConnector.close();
			}
		});
	}

	/**
	 * This Method Returns the value of Consistency Level for Multi Node/DC
	 * Cassandra Cluster.
	 * 
	 * @return ConsistencyLevel
	 */
	private static ConsistencyLevel getConsistencyLevel() {
		String key = "cassandra.consistency.level";
		String consistencyLevel = Platform.config.hasPath(key) ? Platform.config.getString(key) : null;
		if (StringUtils.isNotBlank(consistencyLevel))
			return ConsistencyLevel.valueOf(consistencyLevel.toUpperCase());
		return null;
	}

}
