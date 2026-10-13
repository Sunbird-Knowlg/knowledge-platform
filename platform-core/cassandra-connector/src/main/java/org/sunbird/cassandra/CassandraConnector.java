package org.sunbird.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.policies.DefaultRetryPolicy;
import com.datastax.driver.core.policies.ExponentialReconnectionPolicy;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ServerException;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


public class CassandraConnector {

	private static final Map<String, Session> sessionMap            = new ConcurrentHashMap<>();
	private static final Map<String, Cluster> clusterMap            = new ConcurrentHashMap<>();
	private static final AtomicBoolean        shutdownHookRegistered = new AtomicBoolean(false);

	/** Maximum number of connection attempts made at JVM startup. */
	private static final int  MAX_STARTUP_RETRIES = Platform.getInteger("cassandra.max.startup.retries", 30);
	/** Initial retry wait (doubles each attempt, capped at RETRY_MAX_MS). */
	private static final long RETRY_BASE_MS       = 2_000L;
	/** Ceiling for the retry wait interval. */
	private static final long RETRY_MAX_MS        = 30_000L;

	static {
		if (Platform.getBoolean("service.db.cassandra.enabled", true))
			prepareSessionWithRetry("lp", getConsistencyLevel("lp"));
	}

	public static Session getSession() {
		return getSession("lp");
	}

	/**
	 * Returns the active session for {@code sessionKey}.
	 * If the session or its backing cluster is closed, reconnects once inside a
	 * synchronized block (double-checked locking) to avoid concurrent reconnects.
	 *
	 * @param sessionKey one of "lp", "lpa", "sunbird", "platform-courses"
	 * @return an active Session
	 * @throws ServerException if the session cannot be established
	 */
	public static Session getSession(String sessionKey) {
		String  key     = sessionKey.toLowerCase();
		Session session = sessionMap.get(key);
		Cluster cluster = clusterMap.get(key);

		if (session != null && !session.isClosed() && cluster != null && !cluster.isClosed()) {
			return session;
		}

		synchronized (CassandraConnector.class) {
			session = sessionMap.get(key);
			cluster = clusterMap.get(key);

			if (session == null || session.isClosed() || cluster == null || cluster.isClosed()) {
				try {
					prepareSession(key, getConsistencyLevel(key));
					TelemetryManager.log("Cassandra session re-established for [" + key + "]");
				} catch (Exception e) {
					TelemetryManager.error("Cassandra reconnect failed for [" + key + "]: "
							+ e.getMessage(), e);
					throw new ServerException("ERR_INITIALISE_CASSANDRA_SESSION",
							"Cassandra reconnect failed for [" + key + "]: " + e.getMessage(), e);
				}
				session = sessionMap.get(key);
			}
		}

		if (session == null)
			throw new ServerException("ERR_INITIALISE_CASSANDRA_SESSION",
					"Unable to obtain Cassandra session for key: " + sessionKey);
		return session;
	}

	/**
	 * Closes all Cluster objects, which releases their sessions, connection pools,
	 * and driver-internal background threads. Each cluster is closed independently
	 * so a failure in one does not block the others.
	 */
	public static synchronized void close() {
		clusterMap.forEach((key, cluster) -> {
			if (cluster != null && !cluster.isClosed()) {
				try {
					cluster.close();
				} catch (Exception e) {
					TelemetryManager.error(
							"Error closing Cassandra cluster [" + key + "]: " + e.getMessage(), e);
				}
			}
		});
		sessionMap.clear();
		clusterMap.clear();
	}

	/**
	 * Startup retry loop — called only from the static initialiser.
	 * Retries up to MAX_STARTUP_RETRIES times with exponential backoff and
	 * full jitter so the service can tolerate Cassandra starting after the JVM.
	 */
	private static void prepareSessionWithRetry(String sessionKey, ConsistencyLevel level) {
		int  attempt = 0;
		long cap     = RETRY_BASE_MS;

		while (attempt < MAX_STARTUP_RETRIES) {
			attempt++;
			try {
				prepareSession(sessionKey, level);
				TelemetryManager.log(
						"Cassandra session ready for [" + sessionKey + "] on attempt " + attempt);
				return;
			} catch (Exception e) {
				TelemetryManager.error("Cassandra connect attempt " + attempt + "/"
						+ MAX_STARTUP_RETRIES + " failed for [" + sessionKey + "]: "
						+ e.getMessage(), e);

				if (attempt < MAX_STARTUP_RETRIES) {
					// Full jitter: sleep = random(0, min(cap, RETRY_MAX_MS))
					long sleep = (long) (Math.random() * Math.min(cap, RETRY_MAX_MS));
					try {
						Thread.sleep(sleep);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						throw new ServerException("ERR_INITIALISE_CASSANDRA_SESSION",
								"Cassandra startup retry interrupted for [" + sessionKey + "]");
					}
					cap = Math.min(cap * 2, RETRY_MAX_MS);
				}
			}
		}
		throw new ServerException("ERR_INITIALISE_CASSANDRA_SESSION",
				"All " + MAX_STARTUP_RETRIES
						+ " Cassandra startup connect attempts exhausted for [" + sessionKey + "]");
	}


	/**
	 * Creates a brand-new Cluster and Session for {@code sessionKey} and stores
	 * them in the maps. Closes any previous Cluster for the same key to free its
	 * resources. Throws if no contact point is reachable so callers can react.
	 */
	private static void prepareSession(String sessionKey, ConsistencyLevel level) {
		List<InetSocketAddress> addressList = getSocketAddress(getConnectionInfo(sessionKey));

		Cluster.Builder builder = Cluster.builder()
				.addContactPointsWithPorts(addressList)
				.withReconnectionPolicy(new ExponentialReconnectionPolicy(1_000L, 60_000L))
				.withRetryPolicy(DefaultRetryPolicy.INSTANCE)
				.withoutJMXReporting();

		// Add authentication and TLS if configured
		CassandraSecurity.apply(builder,
				Platform.getString("cassandra.auth.username", ""),
				Platform.getString("cassandra.auth.password", ""),
				Platform.getBoolean("cassandra.ssl.enabled", false),
				Platform.getString("cassandra.ssl.rootcert", ""));

		if (level != null)
			builder.withQueryOptions(new QueryOptions().setConsistencyLevel(level));

		Cluster cluster = builder.build();
		Session session;
		try {
			session = cluster.connect();
		} catch (Exception e) {
			cluster.close();
			throw e;
		}

		Cluster oldCluster = clusterMap.get(sessionKey);
		if (oldCluster != null && !oldCluster.isClosed()) {
			try { oldCluster.close(); } catch (Exception ignored) { /* best effort */ }
		}

		clusterMap.put(sessionKey, cluster);
		sessionMap.put(sessionKey, session);

		if (shutdownHookRegistered.compareAndSet(false, true))
			registerShutdownHook();
	}

	private static void registerShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			TelemetryManager.log("Shutting down Cassandra connector — closing all clusters");
			CassandraConnector.close();
		}));
	}

	private static List<String> getConnectionInfo(String sessionKey) {
		String configKey;
		switch (sessionKey) {
			case "lp":               configKey = "cassandra.lp.connection";               break;
			case "lpa":              configKey = "cassandra.lpa.connection";              break;
			case "sunbird":          configKey = "cassandra.sunbird.connection";          break;
			case "platform-courses": configKey = "cassandra.connection.platform_courses"; break;
			default:                 configKey = null;                                     break;
		}
		if (configKey != null && Platform.config.hasPath(configKey)) {
			List<String> nodes = Arrays.stream(Platform.config.getString(configKey).split(","))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.collect(Collectors.toList());
			if (!nodes.isEmpty()) return nodes;
		}
		return new ArrayList<>(Collections.singletonList("localhost:9042"));
	}

	private static List<InetSocketAddress> getSocketAddress(List<String> hosts) {
		List<InetSocketAddress> list = new ArrayList<>();
		for (String conn : hosts) {
			String[] parts = conn.trim().split(":");
			String host = parts[0].trim();
			int port = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 9042;
			list.add(new InetSocketAddress(host, port));
		}
		return list;
	}

	private static ConsistencyLevel getConsistencyLevel(String clusterName) {
		String key   = "cassandra." + clusterName + ".consistency.level";
		String value = Platform.config.hasPath(key) ? Platform.config.getString(key) : null;
		return StringUtils.isNotBlank(value) ? ConsistencyLevel.valueOf(value.toUpperCase()) : null;
	}
}
