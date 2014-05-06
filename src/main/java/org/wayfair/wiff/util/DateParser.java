package org.wayfair.wiff.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateParser {
  private final static ThreadLocal<SimpleDateFormat> parsers = new ThreadLocal<SimpleDateFormat>() {
                                                               protected SimpleDateFormat initialValue() {
                                                                 return new SimpleDateFormat(
                                                                     "EEE, dd MMM yyyy HH:mm:ss zzz");
                                                               }
                                                             };

  /**
   * Converts a date String in the format "EEE, dd MMM yyyy HH:mm:ss zzz" to a
   * date object
   * 
   * @param date
   *          the String to be converted to a Date
   * @return a Date object representation of the given string
   * @throws ParseException
   */
  public static Date parse(String date) throws ParseException {
    return parsers.get().parse(date);
  }
}
