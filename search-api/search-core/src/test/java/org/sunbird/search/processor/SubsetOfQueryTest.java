package org.sunbird.search.processor;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class SubsetOfQueryTest {

    private final SearchProcessor processor = new SearchProcessor("unused");

    @Test
    public void buildsTermsSetOnTheGivenField() {
        String json = processor.getSubsetOfQuery("skill.raw", Arrays.asList("s1", "s2", "s3")).toString();
        Assert.assertTrue(json, json.contains("\"terms_set\""));
        Assert.assertTrue(json, json.contains("\"skill.raw\""));
        Assert.assertTrue(json, json.contains("\"s1\"") && json.contains("\"s2\"") && json.contains("\"s3\""));
    }

    @Test
    public void requiresEveryValueTheDocumentHolds() {
        String json = processor.getSubsetOfQuery("skill.raw", Arrays.asList("s1")).toString();
        Assert.assertTrue(json, json.contains("Math.max(1, doc[params.field].size())"));
        Assert.assertTrue(json, json.contains("\"field\" : \"skill.raw\""));
    }

    @Test
    public void keepsTheFieldNameOutOfTheScriptSource() {
        String hostile = "skill.raw'].size()); return 0; //";
        String json = processor.getSubsetOfQuery(hostile, Arrays.asList("s1")).toString();
        Assert.assertTrue(json, json.contains("\"source\" : \"Math.max(1, doc[params.field].size())\""));
    }
}
