package org.sunbird.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerUtil {

  private Logger logger;

  public LoggerUtil(Class c) {
    logger = LoggerFactory.getLogger(c);
  }
  public void info(String message) {
    logger.info(message);
  }
  public void error(String message, Throwable e) {
    logger.error(message, e);
  }

}
