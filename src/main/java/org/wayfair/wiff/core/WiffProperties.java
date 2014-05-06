package org.wayfair.wiff.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Properties;

public class WiffProperties implements Serializable {

  private static final long serialVersionUID = 1L;
  protected Properties      properties       = new Properties();

  /**
   * Creates a WiffProperties object using key and value pairs from an input
   * stream
   * 
   * @param reader
   *          the input stream
   * @return a WiffProperties object containing the data from the input stream
   */
  public static WiffProperties createFromStream(BufferedReader reader) {
    WiffProperties props = new WiffProperties();
    try {
      props.properties.load(reader);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return props;
  }

  /**
   * Creates a WiffProperties object using key and value pairs from an file
   * 
   * @param filePath
   *          a path to a file of key=value assignments
   * @return a WiffProperties object containing the data from the file
   */
  public static WiffProperties createFromFile(String filePath) {
    try {
      File config = new File(filePath);
      BufferedReader reader = new BufferedReader(new FileReader(
          config.getAbsoluteFile()));

      return WiffProperties.createFromStream(reader);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Sets the given key equal to the given value
   * 
   * @param key
   * @param value
   */
  public void setProperty(String key, String value) {
    properties.setProperty(key.trim(), value.trim());
  }

  /**
   * Retrieves the value of the given key as a string
   * 
   * @param key
   *          the property to retrieve
   * @return a string containing the key's value
   */
  public String getString(String key) {
    String result = properties.getProperty(key.trim());
    if (result != null) {
      result = result.trim();
    }
    return result;
  }

  /**
   * Retrieves the value of the given key as an int
   * 
   * @param key
   *          the property to retrieve
   * @return the key's value as an int
   */
  public int getInt(String key) {
    return getInt(key, 0);
  }

  /**
   * Retrieves the value of the given key as a float
   * 
   * @param key
   *          the property to retrieve
   * @return the key's value as a float
   */
  public float getFloat(String key) {
    return Float.parseFloat(getString(key));
  }

  /**
   * Retrieves the value of the given key as a boolean
   * 
   * @param key
   *          the property to retrieve
   * @return the key's value as a boolean
   */
  public boolean getBoolean(String key) {
    return Boolean.parseBoolean(getString(key));
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#toString()
   */
  public String toString() {
    return properties.toString().replace(", ", "\n");
  }

  /**
   * Bulk loads the properties object from a map of key, value pairs
   * 
   * @param map
   *          a map of keys and values
   */
  public void setProperities(Map<String, String> map) {
    for (String key : map.keySet()) {
      setProperty(key, map.get(key));
    }
  }

  /**
   * Determines if the given property has a value
   * 
   * @param key
   *          the name of the property
   * @return true if the property has been set. Otherwise, false.
   */
  public boolean contains(String key) {
    return properties.containsKey(key);
  }

  public boolean getBoolean(String key, boolean defaultValue) {
    String result = getString(key);
    if (result != null) {
      return Boolean.parseBoolean(getString(key));
    }
    return defaultValue;
  }

  public String getString(String key, String defaultValue) {
    String result = getString(key);
    if (result != null) {
      return result;
    }
    return defaultValue;
  }

  public int getInt(String key, int defaultValue) {
    try {
      return Integer.parseInt(getString(key));
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public float getFloat(String key, float defaultValue) {
    try {
      return Float.parseFloat(getString(key));
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
