package org.sunbird.graph.service.common;

public class DACConfigurationConstants {

	public static final String DEFAULT_ROUTE_PROP_PREFIX = "route.graph.";

	public static final String DEFAULT_JANUSGRAPH_ROUTE_ID = "all";

	public static final String PASSPORT_KEY_BASE_PROPERTY = "graph.passport.key.base";

	public static final String DOT = ".";

	public static final String UNDERSCORE = "_";

	public static final int JANUSGRAPH_MAX_CONNECTION_POOL_SIZE = 20;

	public static final int JANUSGRAPH_MIN_CONNECTION_POOL_SIZE = 2;

	private DACConfigurationConstants() {
		throw new AssertionError();
	}

}
