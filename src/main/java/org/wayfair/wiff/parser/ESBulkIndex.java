package org.wayfair.wiff.parser;

public class ESBulkIndex implements Parser<String, byte[]> {
  private StringBuilder message = new StringBuilder(10000);
  private String        prefix;
  private String        suffix  = " }\n";

  public ESBulkIndex(String indexName) {
    prefix = "{ \"index\" : { \"_index\" : \"" + indexName
        + "\", \"_type\" : \"data\" } }\n{ \"data\" : ";
  }

  @Override
  public byte[] parse(String json) {
    message.append(prefix).append(json).append(suffix);
    
    byte[] msg = message.toString().getBytes();
    message.delete(0, message.length());
    return msg;
  }
}
