package org.sunbird.graph.common;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class IdentifierTest {
    @BeforeClass
    public static void init() {

    }

    @Test
    public void getUniqueIdFromNeo4jId() throws Exception {
        String id = Identifier.getUniqueIdFromNeo4jId(System.currentTimeMillis());
        System.out.println(id);
        Assert.assertTrue(StringUtils.endsWith(id, "1"));
    }

    @Test
    public void getUniqueIdFromTimestamp() throws Exception {
        String id = Identifier.getUniqueIdFromTimestamp();
        System.out.println(id);
        Assert.assertTrue(StringUtils.endsWith(id, "1"));
    }

    @Test
    public void getIdentifier() throws Exception {
        String id = Identifier.getIdentifier("domain", "1234");
        System.out.println(id);
        Assert.assertTrue(StringUtils.equals(id, "do_1234"));
    }

}
