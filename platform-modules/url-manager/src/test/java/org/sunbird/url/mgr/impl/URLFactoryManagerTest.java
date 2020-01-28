package org.sunbird.url.mgr.impl;

import org.junit.Test;
import org.sunbird.common.exception.ClientException;
import org.sunbird.url.mgr.IURLManager;

import static org.junit.Assert.assertTrue;


/**
 * Test Class Holds Unit Test Cases For UrlManager
 *
 * @see URLFactoryManager
 */
public class URLFactoryManagerTest {

	@Test
	public void testGetUrlManager() {
		IURLManager yMgr = URLFactoryManager.getUrlManager("youtube");
		assertTrue(yMgr instanceof YouTubeURLManagerImpl);
		IURLManager gdMgr = URLFactoryManager.getUrlManager("googledrive");
		assertTrue(gdMgr instanceof GoogleDriveURLManagerImpl);
		IURLManager genMgr = URLFactoryManager.getUrlManager("general");
		assertTrue(genMgr instanceof GeneralURLManagerImpl);
	}

	@Test(expected = ClientException.class)
	public void testGetUrlManagerWithInvalidInput() {
		IURLManager yMgr = URLFactoryManager.getUrlManager("icloud");
	}
}
