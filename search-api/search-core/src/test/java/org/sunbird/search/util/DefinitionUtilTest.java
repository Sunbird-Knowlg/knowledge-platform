package org.sunbird.search.util;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

public class DefinitionUtilTest {
    @Test
    public void testAddDocumentWithId() throws Exception {
        Map<String,Object> metadata = DefinitionUtil.getMetaData("content");
        Assert.assertNotNull(metadata);
        Assert.assertEquals(Arrays.asList("Live"), metadata.get("status"));
    }
}
