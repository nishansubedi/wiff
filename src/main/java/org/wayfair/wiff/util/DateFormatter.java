package org.wayfair.wiff.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateFormatter {
  private final static ThreadLocal<SimpleDateFormat> parsers = new ThreadLocal<SimpleDateFormat>() {
                                                               protected SimpleDateFormat initialValue() {
                                                                 return new SimpleDateFormat(
                                                                     "yyyy-MM-dd'T'HH:mm:ssZ");
                                                               }
                                                             };

  /**
   * @param date
   *          the date to be formatted
   * @returna a String representation of a given date in the format
   *          yyyy-MM-dd'T'HH:mm:ssZ
   */
  public static String format(Date date) {
    return parsers.get().format(date);
  }
}