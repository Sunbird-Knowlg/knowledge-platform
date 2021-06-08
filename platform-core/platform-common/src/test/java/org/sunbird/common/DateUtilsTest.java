package org.sunbird.common;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;


public class DateUtilsTest {

    @Test
    public void testFormatValidDate() throws Exception {
        String date = DateUtils.format(new Date());
        System.out.println(date);
        Assert.assertNotNull(date);
        Assert.assertTrue(StringUtils.containsAny(date, "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z"));
    }

    @Test
    public void testFormatNullDate() throws Exception {
        String date = DateUtils.format(null);
        Assert.assertNull(date);
    }

    @Test
    public void testParseValidString() throws Exception {
        Date date = DateUtils.parse("2020-08-12T14:34:18.691+0530");
        Assert.assertNotNull(date);
        Assert.assertNotNull(date.getTime());
    }

    @Test
    public void testParseInvalidString() throws Exception {
        Date date = DateUtils.parse("0000000");
        Assert.assertNull(date);
    }

    @Test
    public void testGetDateFormat() throws Exception {
        SimpleDateFormat sdf = DateUtils.getDateFormat();
        Assert.assertNotNull(sdf);
        Assert.assertTrue(StringUtils.containsAny(sdf.get2DigitYearStart().toString(), "\\w+\\s?\\w+\\s?\\d{2}\\s?\\d{2}:\\d{2}:\\d{2}.\\d{3}Z"));
    }

    @Test
    public void testformatCurrentDate() throws Exception {
        String date = DateUtils.formatCurrentDate();
        Assert.assertNotNull(date);
        Assert.assertTrue(StringUtils.containsAny(date, "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z"));
    }

    @Test
    public void testformatCurrentDateWithPattern() throws Exception {
        String date = DateUtils.formatCurrentDate("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        Assert.assertNotNull(date);
        Assert.assertTrue(StringUtils.containsAny(date, "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z"));
    }

    @Test
    public void testformatCurrentDateWithInvalidPattern() throws Exception {
        String date = DateUtils.formatCurrentDate("");
        Assert.assertTrue(StringUtils.isAllBlank(date));
    }

}
