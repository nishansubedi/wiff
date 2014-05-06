package org.wayfair.wiff.parser;

/**
 * Transforms data from one type to another
 * 
 * @param <T>
 *          the input type
 * @param <S>
 *          the output type
 */
public interface Parser<T, S> {

  public enum Parsers {
    HTTPParser, PrependSize, ESBulkIndex;
  }

  /**
   * Transforms object of type T to type S
   * 
   * @param obj
   *          the object to transform
   * @return the representation of obj as type S
   */
  public S parse(T obj);
}
