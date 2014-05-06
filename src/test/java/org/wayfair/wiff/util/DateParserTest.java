package org.wayfair.wiff.util;

import static org.junit.Assert.*;

import java.text.DateFormat;
import java.text.ParseException;
import org.junit.Test;

public class DateParserTest {

  @Test
  public void test() {
    DateFormat df = DateFormat.getDateTimeInstance(DateFormat.FULL,
        DateFormat.FULL);
    String date = null;
    try {
      date = "Fri, 14 Feb 2014 13:02:03 GMT";
      assertEquals("Friday, February 14, 2014 8:02:03 AM EST",
          df.format(DateParser.parse(date)));

      date = "Fri, 14 Feb 2014 01:33:38 GMT";
      assertEquals("Thursday, February 13, 2014 8:33:38 PM EST",
          df.format(DateParser.parse(date)));

      date = "Mon, 16 Dec 2013 19:08:10 GMT";
      assertEquals("Monday, December 16, 2013 2:08:10 PM EST",
          df.format(DateParser.parse(date)));

    } catch (ParseException e) {
      fail("Could not parse date: " + date);
    }
  }
}
