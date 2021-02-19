package org.sunbird.common;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class SlugTest {

    @Test
    public void testMakeSlug() throws Exception {
       String sluggified =  Slug.makeSlug(" -Cov -e*r+I/ αma.ge.png-- ");
        Assert.assertEquals("cov-er-i-ma.ge.png", sluggified);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMakeSlugException() throws Exception {
        Slug.makeSlug(null);
    }

    @Test
    public void testMakeSlugTransiliterate() throws Exception {
        String sluggified =  Slug.makeSlug(" Cov -e*r+I/ αma.ge.png ", true);
        Assert.assertEquals("cov-er-i-ama.ge.png", sluggified);
    }

    @Test
    public void testremoveDuplicateCharacters() throws Exception {
        String sluggified =  Slug.removeDuplicateChars("akssaaklla");
        Assert.assertEquals("aksakla", sluggified);
    }

    @Test
    public void testcreateSlugFile() throws Exception {
        File file = new File("-αimage.jpg");
        File slugFile =  Slug.createSlugFile(file);
        Assert.assertEquals("aimage.jpg", slugFile.getName());
    }

}
