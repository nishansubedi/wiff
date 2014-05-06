package org.wayfair.wiff.parser;

import static org.wayfair.wiff.util.ParseFunctions.*;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import org.apache.log4j.Logger;
import org.wayfair.wiff.util.DateFormatter;
import org.wayfair.wiff.util.DateParser;

public class HTTPParser implements Parser<byte[], byte[]> {
  public static final String headerIdentifier = "HTTP/";
  public static final String lineEnding       = "\r\n";
  public static final String blankLine        = "\r\n\r\n";

  private String             index, dataSource, inputSource;
  private int                indexNameOffset;
  private boolean            includeBody;

  private StringBuilder      messageBuffer    = new StringBuilder(200000);
  private StringBuilder      message          = new StringBuilder(100000);

  private final Logger       LOGGER           = Logger.getLogger(this
                                                  .getClass());

  /**
   * @param index
   *          the elasticsearch index to which this data belongs. (the date/time
   *          will be appended)
   * @param includeBody
   *          indicates whether or not request/response bodies should be
   *          extracted
   */
  public HTTPParser(String index, boolean includeBody, String dataSource,
      String inputSource) {
    this.index = index + "-YYYY.MM.DD.HH";
    indexNameOffset = index.length() + 27;
    this.includeBody = includeBody;
    this.dataSource = dataSource;
    this.inputSource = inputSource;
  }

  /**
   * Transforms a TCP stream into a message for elasticsearch using the Bulk API
   * syntax.
   * 
   * @param data
   *          the bytes of the the TCP stream
   */
  public byte[] parse(byte[] data) {
    String stream = new String(data);

    int requestStart = stream.lastIndexOf(lineEnding,
        stream.indexOf(headerIdentifier));
    int requestEnd, responseEnd, time;

    // Get top level data
    String topLevel = null;
    if (requestStart < 0) {
      requestStart = 0;
    } else {
      topLevel = stream.substring(0, requestStart);
      requestStart += 2;
    }

    while (requestStart > -1 && requestStart < stream.length()) {
      /*
       * Check for 2 HTTP/ tokens (request and response) with a blank line
       * between them. The second HTTP/ is the end of the request.
       */
      requestEnd = stream.indexOf(headerIdentifier, requestStart);
      requestEnd = stream.indexOf(blankLine, requestEnd);
      requestEnd = stream.indexOf(headerIdentifier, requestEnd);
      if (requestEnd < 0) {
        LOGGER.debug("Could not parse stream:\n"
            + stream.substring(requestStart));
        break;
      }

      /*
       * The end of the response is either the next blank line or the end of the
       * stream.
       */
      responseEnd = stream.indexOf(headerIdentifier, requestEnd + 5);
      if (responseEnd < 0) {
        responseEnd = stream.length();
      } else {
        responseEnd = stream.lastIndexOf(lineEnding, responseEnd) + 2;
      }
      message
          .append("{ \"index\" : { \"_index\" : \"")
          .append(index)
          .append("\", \"_type\" : \"data\" } }\n{ \"data\" : { \"request\" : ");

      if (parseRequest(stream, requestStart, requestEnd, includeBody, message)) {
        message.append(", \"response\" : ");
        time = parseResponse(stream, requestEnd, responseEnd, includeBody,
            message);

        if (time >= 0) {
          // Create message
          message.replace(indexNameOffset, indexNameOffset + 4,
              Integer.toString(time / 1000000));
          message.replace(indexNameOffset + 5, indexNameOffset + 7,
              String.format("%02d", (time % 1000000) / 10000));
          message.replace(indexNameOffset + 8, indexNameOffset + 10,
              String.format("%02d", (time % 10000) / 100));
          message.replace(indexNameOffset + 11, indexNameOffset + 13,
              String.format("%02d", (time % 100)));

          if (topLevel != null && topLevel.length() > 0) {
            message.append(", ").append(topLevel);
          }
          if (dataSource != null) {
            appendKeyValue(message, "data_source", dataSource);
          }
          if (inputSource != null) {
            appendKeyValue(message, "input_source", inputSource);
          }
          message.append(" } }").append("\n");
          messageBuffer.append(message);
        }
      }

      message.delete(0, message.length());
      // Update requestStart so check for other conversations in this stream
      requestStart = responseEnd;
    }
    byte[] msg = messageBuffer.toString().getBytes();
    messageBuffer.delete(0, messageBuffer.length());

    return msg;
  }

  /**
   * Parses the request headers and body (optional) of a given stream found
   * within the given range using the elasticseach Bulk API syntax
   * 
   * @param stream
   *          the stream to parse
   * @param start
   *          the character offset of the request headers
   * @param end
   *          the character offset of the end of the request headers or body (if
   *          present)
   * @param includeBody
   *          indicates whether or not the request body should be extracted
   * 
   * @param dst
   *          the Stringbuilder to which the parsed data will be appended
   * @return true if the parsing was successful, otherwise false.
   */
  public boolean parseRequest(String stream, int start, int end,
      boolean includeBody, StringBuilder dst) {
    // determine the start and end of the header
    int headerStart = stream.indexOf(headerIdentifier, start) - 1;
    int headerEnd = stream.indexOf(blankLine, headerStart);

    if (headerStart < 0 || headerEnd < 0 || headerStart > end
        || headerEnd > end) {
      LOGGER.debug("Unable to parse request:\n" + stream);
      return false;
    }

    dst.append("{ ");
    StringBuilder cookies = new StringBuilder(2000);
    cookies.append("{ ");
    /*
     * Work backwards from the HTTP/ to find the query string, url, and request
     * method
     */
    String page;
    int page_start;
    int querystring_start = stream.lastIndexOf("?", headerStart);
    if (querystring_start > start) {
      page_start = stream.lastIndexOf(" ", querystring_start) + 1;
      page = stream.substring(page_start, querystring_start);
      appendKeyValue(dst, "query",
          stream.substring(querystring_start + 1, headerStart));
    } else {
      page_start = stream.lastIndexOf(" ", headerStart - 1) + 1;
      page = stream.substring(page_start, headerStart);
    }
    appendKeyValue(dst, "page", page);

    int method_start = page_start - 2;
    char c;
    do {
      c = stream.charAt(method_start);
      method_start--;
    } while (Character.isLetter(c));

    appendKeyValue(dst, "method",
        stream.substring(method_start + 2, page_start - 1));

    appendKeyValue(dst, "http_version",
        stream.substring(headerStart + 6, headerStart + 9));

    // Parse headers
    int match_start, match_value_start, match_end;
    match_start = stream.indexOf(lineEnding, start) + 2;

    while (match_start > 0) {
      match_value_start = stream.indexOf(": ", match_start);
      match_end = stream.indexOf(lineEnding, match_value_start);
      if (match_value_start < 0 || match_end > headerEnd) {
        break;
      }

      if (match_end < 0) {
        match_end = stream.length();
      }

      String key = stream.substring(match_start, match_value_start)
          .toLowerCase().trim();
      String value = stream.substring(match_value_start + 2, match_end).trim();

      if (key.equals("cookie")) {
        // parse and aggregate cookies
        parseCookies(value, cookies);
      } else {
        // parse all other headers
        appendKeyValue(dst, key, value);
      }
      match_start = match_end + 1;
    }

    if (cookies.length() > 2) {
      appendKeyObject(dst, "cookie", cookies.append(" }"));
    }

    if (includeBody) {
      String body = stream.substring(headerEnd + blankLine.length(), end);
      if (body.length() > 0) {
        appendKeyValue(dst, "body",
            escape(stream.substring(headerEnd + blankLine.length(), end)));
      }
    }
    dst.append(" }");
    return true;
  }

  /**
   * Parses the request headers and body (optional) of a given stream found
   * within the given range using the elasticseach Bulk API syntax
   * 
   * @param stream
   *          the stream to parse
   * @param start
   *          the character offset of the response headers
   * @param end
   *          the character offset of the end of the reponse headers or body (if
   *          present)
   * @param includeBody
   *          indicates whether or not the response body should be extracted
   * 
   * @param dst
   *          the Stringbuilder to which the parsed data will be appended
   * @return an integer respresenting the date. The format is YYYYMMDDHH. -1
   *         will be returned if parsing fails.
   */
  @SuppressWarnings("deprecation")
  public int parseResponse(String stream, int start, int end,
      boolean includeBody, StringBuilder dst) {

    Date date = null;

    int headerEnd = stream.indexOf(blankLine, start);

    // find the end of the response code
    int respCodeEnd = stream.indexOf(lineEnding, start);

    /*
     * If there isnt enough room for a http version, status, and status code, we
     * must have erred in stitching
     */
    if (start + 13 > respCodeEnd) {
      LOGGER.debug("Unable to parse response:\n" + stream);
      return -1;
    }

    int time = -1;
    dst.append("{ ");
    StringBuilder cookies = new StringBuilder(250);
    cookies.append("{ ");

    // get the 3 digit HTTP status code located after the HTTP version
    appendKeyValue(dst, "http_status_code",
        stream.substring(start + 9, start + 12));

    // get the HTTP status located after the status code and before the new line
    appendKeyValue(dst, "http_status",
        stream.substring(start + 13, respCodeEnd));

    // Parse headers
    int match_start, match_value_start, match_end;
    match_start = stream.indexOf(lineEnding, start) + 2;

    String key, value;
    int contentLength = 0;
    boolean isGzipped = false;
    boolean hasDate = false;
    while (match_start > 0 && match_start < headerEnd) {
      match_value_start = stream.indexOf(": ", match_start);
      match_end = stream.indexOf(lineEnding, match_value_start);
      if (match_value_start < 0 || match_end > headerEnd) {
        break;
      }

      if (match_end < 0) {
        match_end = stream.length();
      }

      key = stream.substring(match_start, match_value_start).toLowerCase()
          .trim();
      value = stream.substring(match_value_start + 2, match_end).trim();

      if (key.equals("set-cookie")) {
        // parse and aggregate cookies
        parseCookies(value, cookies);
      } else {
        // parse all other headers
        appendKeyValue(dst, key, value);

        if (key.equals("date") && !value.isEmpty()) {
          // use this date as a timestamp for the stream
          try {
            hasDate = true;
            date = DateParser.parse(value);
          } catch (ParseException e) {
            LOGGER.error("", e);
          }
        } else if (key.equals("content-encoding") && value.equals("gzip")) {
          isGzipped = true;
        } else if (key.equals("content-length")) {
          contentLength = Integer.parseInt(value);
        }
      }
      match_start = match_end + 2;
    }

    // add any cookies we've found
    if (cookies.length() > 2) {
      appendKeyObject(dst, "set-cookie", cookies.append(" }"));
    }

    // if the response is missing the date, use the system date
    if (!hasDate) {
      date = Calendar.getInstance().getTime();
    }

    if (includeBody) {
      if (headerEnd < end && contentLength > 0) {
        String body = stream.substring(headerEnd + blankLine.length(), end);

        if (isGzipped) {
          byte[] bytes = body.getBytes();
          bytes = Arrays.copyOfRange(bytes, bytes.length - contentLength,
              bytes.length);
          appendKeyValue(dst, "body", decompress(bytes));
        } else {
          appendKeyValue(dst, "body", escape(body));
        }
      }
    }
    dst.append(" }");
    if (date != null) {
      appendKeyValue(dst, "timestamp", DateFormatter.format(date));
      time = (date.getYear() + 1900) * 1000000;
      time += (date.getMonth() + 1) * 10000;
      time += date.getDate() * 100;
      time += date.getHours();
    }
    return time;
  }

  /**
   * Parses a cookies string using the elasticseach Bulk API syntax
   * 
   * @param cookies
   *          the string to be parsed
   * @param json
   *          the Stringbuilder to which the parsed data will be appened
   */
  public static void parseCookies(String cookies, StringBuilder json) {
    int cookie_start, cookie_end, cookie_val_start, end;
    cookie_start = 0;
    end = cookies.length();

    while (cookie_start < end) {
      cookie_val_start = cookies.indexOf("=", cookie_start);
      if (cookie_val_start < 0) {
        break;
      }
      cookie_end = cookies.indexOf(";", cookie_val_start);
      if (cookie_end < 0) {
        cookie_end = cookies.length();
      }

      String val = cookies.substring(cookie_val_start + 1, cookie_end);
      if (val.length() > 0) {
        // remove quotes, elasticsearch doesn't like them
        val.replace("\"", "");
        appendKeyValue(json, cookies.substring(cookie_start, cookie_val_start),
            val);
      }
      cookie_start = cookie_end + 2;
    }
  }
}
