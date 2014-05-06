package org.wayfair.wiff.util.ssl;

import javax.crypto.Cipher;

import org.apache.log4j.Logger;

public class CipherSuite {
  private final byte   identifer;
  private final String transformation;
  private final int    blockSize;
  private final int    macKeySize;
  private final int    writeKeySize;

  public static enum CipherSuites {
    TLS_RSA_WITH_RC4_128_SHA((byte) 0x05, "RC4", 0, 20, 16), TLS_RSA_WITH_3DES_EDE_CBC_SHA(
        (byte) 0x0a, "DESede/CBC/NoPadding", 8, 20, 24), TLS_RSA_WITH_AES_256_CBC_SHA(
        (byte) 0x35, "AES/CBC/NoPadding", 16, 20, 32), TLS_RSA_WITH_AES_128_CBC_SHA(
        (byte) 0x2f, "AES/CBC/NoPadding", 16, 20, 16), TLS_RSA_WITH_CAMELLIA_128_CBC_SHA(
        (byte) 0x41, "Camellia/CBC/NoPadding", 16, 20, 16), TLS_RSA_WITH_CAMELLIA_256_CBC_SHA(
        (byte) 0x84, "Camellia/CBC/NoPadding", 16, 20, 32);

    private final CipherSuite cipherSuite;

    private CipherSuites(byte identifier, String transformation, int blockSize,
        int macKeySize, int writeKeySize) {
      cipherSuite = new CipherSuite(identifier, transformation, blockSize,
          macKeySize, writeKeySize);
    }
  }

  private static final Logger LOGGER = Logger.getLogger(CipherSuite.class);

  private CipherSuite(byte identifier, String transformation, int blockSize,
      int macKeySize, int writeKeySize) {
    this.identifer = identifier;
    this.transformation = transformation;
    this.blockSize = blockSize;
    this.macKeySize = macKeySize;
    this.writeKeySize = writeKeySize;
  }

  public String getTransformation() {
    return transformation;
  }

  public int getBlockSize() {
    return blockSize;
  }

  public int getMacKeySize() {
    return macKeySize;
  }

  public int getWriteKeySize() {
    return writeKeySize;
  }

  public Cipher getCipher() {
    return SSLFunctions.getCipher(transformation);
  }

  public static CipherSuite lookup(byte[] identifier) {
    if (identifier[0] != 0x00 || identifier.length != 2) {
      LOGGER.error("Unsupported cipher suite");
      return null;
    }
    return lookup(identifier[1]);
  }

  public static CipherSuite lookup(byte identifier) {
    for (CipherSuites suite : CipherSuites.values()) {
      if (suite.cipherSuite.identifer == identifier) {
        return suite.cipherSuite;
      }
    }
    LOGGER.error("Unsupported cipher suite");
    return null;
  }
}
