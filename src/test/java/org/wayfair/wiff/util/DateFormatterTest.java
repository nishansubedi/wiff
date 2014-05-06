package org.wayfair.wiff.util;

import static org.junit.Assert.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;
import org.junit.Test;

public class DateFormatterTest {
  private SimpleDateFormat parser = new SimpleDateFormat(
                                      "EEE, dd MMM yyyy HH:mm:ss zzz");
  private final Logger     LOGGER = Logger.getLogger(this.getClass());

  @Test
  public void test() {
    try {
      Date d = parser.parse("Fri, 14 Feb 2014 13:02:03 GMT");
      assertEquals("2014-02-14T08:02:03-0500", DateFormatter.format(d));

      d = parser.parse("Fri, 14 Feb 2014 01:33:38 GMT");
      assertEquals("2014-02-13T20:33:38-0500", DateFormatter.format(d));

      d = parser.parse("Mon, 16 Dec 2013 19:08:10 GMT");
      assertEquals("2013-12-16T14:08:10-0500", DateFormatter.format(d));
    } catch (ParseException e) {
      LOGGER.error("", e);
    }
  }
}
