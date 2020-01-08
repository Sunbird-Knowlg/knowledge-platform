package org.sunbird.telemetry.handler;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the custom logger implementation to carry out platform Logging. This
 * class holds methods used to log events which are pushed to Kafka topic.
 * 
 * @author mahesh
 *
 */
public class TelemetryLoggingHandler implements TelemetryHandler {

	private static final Logger rootLogger = LoggerFactory.getLogger("DefaultPlatformLogger");
	private static final Logger telemetryLogger = LoggerFactory.getLogger("TelemetryEventLogger");
	

	public void send(String event, Level level) {
		send(event, level, false);
	}
	
	/**
	 * 
	 */
	public void send(String event, Level level, boolean telemetry) {
		if (telemetry) {
			telemetryLogger.info(event);
		} else {
			switch(level) {
				case INFO:
					rootLogger.info(event);
					break;
				case DEBUG:
					rootLogger.debug(event);
					break;
				case ERROR:
					rootLogger.error(event);
					break;
				case WARN:
					rootLogger.warn(event);
					break;
				case TRACE:
					rootLogger.trace(event);
					break;
			}
		}
		
	}

}
