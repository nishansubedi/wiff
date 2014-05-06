package org.wayfair.wiff.util.ssl;

import static org.wayfair.wiff.util.ssl.SSLFunctions.generateKeyMaterial;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.Arrays;

import javax.crypto.Cipher;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.wayfair.wiff.util.ByteArrayFunctions.*;

import org.apache.log4j.Logger;

public class KeyExpansion {
  private byte[][]            iv     = new byte[2][];
  private SecretKeySpec[]     keys   = new SecretKeySpec[2];
  private final CipherSuite   cipherSuite;

  private final Cipher        server_decryptor;
  private final Cipher        client_decryptor;

  private static final Logger LOGGER = Logger.getLogger(KeyExpansion.class);

  /**
   * @param suiteID
   * @param master_secret
   * @param client_random
   * @param server_random
   */
  public KeyExpansion(byte[] suiteID, byte[] master_secret,
      byte[] client_random, byte[] server_random, int minorVersion) {

    cipherSuite = CipherSuite.lookup(suiteID);
    String transformation = cipherSuite.getTransformation();
    int blockSize = cipherSuite.getBlockSize();
    int writeKeySize = cipherSuite.getWriteKeySize();
    int macKeySize = cipherSuite.getMacKeySize();

    client_decryptor = cipherSuite.getCipher();
    server_decryptor = cipherSuite.getCipher();

    if (master_secret == null) {
      master_secret = null;
    }

    byte[] keyMaterial = generateKeyMaterial(master_secret, server_random,
        client_random, 2 * (blockSize + writeKeySize + macKeySize),
        minorVersion);

    int offset = 2 * macKeySize;

    byte[] client_write_key = Arrays.copyOfRange(keyMaterial, offset, offset
        + writeKeySize);
    offset += writeKeySize;

    keys[0] = new SecretKeySpec(client_write_key, transformation);

    byte[] server_write_key = Arrays.copyOfRange(keyMaterial, offset, offset
        + writeKeySize);
    offset += writeKeySize;
    keys[1] = new SecretKeySpec(server_write_key, transformation);
    
    try {
      if (blockSize > 0) {
        iv[0] = Arrays.copyOfRange(keyMaterial, offset, offset + blockSize);

        offset += blockSize;
        iv[1] = Arrays.copyOfRange(keyMaterial, offset, offset + blockSize);

        client_decryptor.init(Cipher.DECRYPT_MODE, keys[0],
            new IvParameterSpec(iv[0]));

        server_decryptor.init(Cipher.DECRYPT_MODE, keys[1],
            new IvParameterSpec(iv[1]));

      } else {
        client_decryptor.init(Cipher.DECRYPT_MODE, keys[0]);
        server_decryptor.init(Cipher.DECRYPT_MODE, keys[1]);
      }
    } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
      LOGGER.error("", e);
    }

    if (blockSize > 0) {

    }
  }

  public SecretKeySpec getClientKey() {
    return keys[0];
  }

  public SecretKeySpec getServerKey() {
    return keys[1];
  }

  public byte[] getClientIV() {
    return iv[0];
  }

  public byte[] getServerIV() {
    return iv[1];
  }

  public void setClientIV(byte[] iv) {
    try {
      this.iv[0] = iv;
      client_decryptor.init(Cipher.DECRYPT_MODE, keys[0], new IvParameterSpec(
          iv));
    } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
      LOGGER.error("", e);
    }
  }

  public void setServerIV(byte[] iv) {
    try {
      this.iv[1] = iv;
      server_decryptor.init(Cipher.DECRYPT_MODE, keys[1], new IvParameterSpec(
          iv));
    } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
      LOGGER.error("", e);
    }
  }

  public byte[] getIV(boolean isClient) {
    if (isClient) {
      return iv[0];
    }
    return iv[1];
  }

  public void setIV(byte[] iv, boolean isClient) {
    if (isClient) {
      setClientIV(iv);
    } else {
      setServerIV(iv);
    }
  }

  public SecretKeySpec getKey(boolean isClient) {
    if (isClient) {
      return keys[0];
    }
    return keys[1];
  }

  public byte[] decrypt(byte[] data, int offset, int length, boolean isClient) {
    byte[] decrypted = null;
    Cipher cipher;
    if (isClient) {
      cipher = client_decryptor;
    } else {
      cipher = server_decryptor;
    }
    int blockSize = cipherSuite.getBlockSize();

    byte[] encrypted = Arrays.copyOfRange(data, offset, offset + length
        - blockSize);
    LOGGER.debug("encrypted data: "
        + bytesToHexString(encrypted, 0, encrypted.length));

    byte[] k = getKey(isClient).getEncoded();
    LOGGER.debug("key: " + bytesToHexString(k, 0, k.length));

    decrypted = cipher.update(encrypted);

    LOGGER.debug("decrypted data: "
        + bytesToHexString(decrypted, 0, decrypted.length));

    if (blockSize > 0) {
      byte[] new_iv = Arrays.copyOfRange(data, offset + length - blockSize,
          offset + length);
      setIV(new_iv, isClient);
    }
    return decrypted;
  }
}
