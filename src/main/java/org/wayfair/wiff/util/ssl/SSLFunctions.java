package org.wayfair.wiff.util.ssl;

import static org.wayfair.wiff.util.ByteArrayFunctions.bytesToHexString;
import static org.wayfair.wiff.util.ByteArrayFunctions.xor;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.log4j.Logger;

import sun.misc.BASE64Decoder;

public class SSLFunctions {
  public static final byte    CONTENT_Handshake        = (byte) 0x16;
  public static final byte    CONTENT_AppData          = (byte) 0x17;
  public static final byte    CONTENT_ChangeCipherSpec = (byte) 0x14;

  public static final byte    HS_HelloRequest          = (byte) 0x00;
  public static final byte    HS_ClientHello           = (byte) 0x01;
  public static final byte    HS_ServerHello           = (byte) 0x02;
  public static final byte    HS_NewSessionTicket      = (byte) 0x04;
  public static final byte    HS_Certificate           = (byte) 0x0b;
  public static final byte    HS_ServerKeyExchng       = (byte) 0x0c;
  public static final byte    HS_CertificateReq        = (byte) 0x0d;
  public static final byte    HS_ServerHelloDone       = (byte) 0x0e;
  public static final byte    HS_CertificateVer        = (byte) 0x0f;
  public static final byte    HS_ClientKeyExchng       = (byte) 0x10;
  public static final byte    HS_Finished              = (byte) 0x14;

  private static final byte[] master                   = "master secret"
                                                           .getBytes();

  private static final byte[] keyExpansion             = "key expansion"
                                                           .getBytes();

  public static enum Hash {
    MD5(64), SHA(64);

    private final int blockSize;

    private Hash(int blockSize) {
      this.blockSize = blockSize;
    }
  }

  private static final Logger LOGGER = Logger.getLogger(SSLFunctions.class);

  public static RSAPrivateKey readPKCS1(byte[] key) throws ASN1FormatException {
    RSAPrivateKey privateKey = null;
    try {
      ASN1Sequence seq = new ASN1Sequence(key);
      seq.stepIntoASN1Object();

      // skip version
      seq.skipASN1Object();

      // Get the modulus
      BigInteger modulus = new BigInteger(seq.extractANS1Object());

      // skip public exponent
      seq.skipASN1Object();

      // get private exponent
      BigInteger private_exponent = new BigInteger(seq.extractANS1Object());

      RSAPrivateKeySpec keySpec = new RSAPrivateKeySpec(modulus,
          private_exponent);

      KeyFactory kf = KeyFactory.getInstance("RSA");
      privateKey = (RSAPrivateKey) kf.generatePrivate(keySpec);

    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      LOGGER.error("", e);
    }
    return privateKey;
  }

  public static RSAPrivateKey readPKCS8(byte[] key) throws ASN1FormatException {
    RSAPrivateKey privateKey = null;

    try {
      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key);
      KeyFactory kf = KeyFactory.getInstance("RSA");

      privateKey = (RSAPrivateKey) kf.generatePrivate(keySpec);

    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      LOGGER.error("", e);
    }
    return privateKey;
  }

  public static RSAPrivateKey readPrivateKeyFile(String filename)
      throws FileNotFoundException, IOException {
    StringBuilder sb = new StringBuilder();
    BufferedReader br = new BufferedReader(new FileReader(filename));
    boolean isPCSK1 = false;

    String line = br.readLine();
    while (line != null) {
      if (line.contains("PRIVATE KEY")) {
        if (line.contains("RSA")) {
          isPCSK1 = true;
        }
        line = br.readLine();
        continue;
      }
      sb.append(line);
      sb.append(System.lineSeparator());
      line = br.readLine();
    }
    br.close();
    byte[] key = (new BASE64Decoder()).decodeBuffer(sb.toString());

    RSAPrivateKey pk = null;

    try {
      if (isPCSK1) {
        pk = readPKCS1(key);
      } else {
        pk = readPKCS8(key);
      }
    } catch (ASN1FormatException e) {
      LOGGER.error("", e);
    }
    return pk;
  }

  public static byte[] generateKeyMaterial(byte[] secret, byte[] rand1,
      byte[] rand2, int minSize, int minorVersion) {
    if (minorVersion > 0) {
      return TLS31_prf(secret, keyExpansion, rand1, rand2, minSize);
    }

    ByteArrayOutputStream output = new ByteArrayOutputStream(minSize);
    try {
      MessageDigest digestMD5 = MessageDigest.getInstance("MD5");
      MessageDigest digestSHA = MessageDigest.getInstance("SHA");
      String prefix = "A";
      int count = 0;

      while (output.size() < minSize) {
        for (int i = 0; i < count + 1; i++) {
          digestSHA.update(prefix.getBytes());
        }
        digestSHA.update(secret);
        digestSHA.update(rand1);
        digestSHA.update(rand2);

        digestMD5.update(secret);
        digestMD5.update(digestSHA.digest());
        output.write(digestMD5.digest());

        digestSHA.reset();
        digestMD5.reset();

        prefix = String.valueOf((char) (prefix.charAt(0) + 1));
        count++;
      }
    } catch (IOException | NoSuchAlgorithmException e) {
      e.printStackTrace();
    }

    return output.toByteArray();

  }

  public static byte[] generateMasterSecret(byte[] premasterSecret,
      byte[] clientRandom, byte[] serverRandom, Cipher cipher,
      PrivateKey privateKey, int minorVersion, String serverIp) {
    byte[] masterSecret = null;
    try {
      cipher.init(Cipher.DECRYPT_MODE, privateKey);

      byte[] decrypted_premaster = cipher.doFinal(premasterSecret);
      LOGGER
          .debug("decrypted_premaster"
              + bytesToHexString(decrypted_premaster, 0,
                  decrypted_premaster.length));

      if (minorVersion > 0) {
        masterSecret = TLS31_prf(decrypted_premaster, master, clientRandom,
            serverRandom, 48);

      } else {
        masterSecret = generateKeyMaterial(decrypted_premaster, clientRandom,
            serverRandom, 48, minorVersion);
      }
    } catch (InvalidKeyException | IllegalBlockSizeException
        | BadPaddingException e) {
      LOGGER.error(serverIp, e);
    }
    return masterSecret;
  }

  public static byte[] TLS31_prf(byte[] secret, byte[] seed_prefix,
      byte[] rand1, byte[] rand2, int minSize) {
    byte[] keyMaterial = null;

    // make hash seed
    byte[] hash_seed = null;
    try {
      ByteArrayOutputStream seed = new ByteArrayOutputStream(secret.length
          + rand1.length + rand2.length);
      seed.write(seed_prefix);
      seed.write(rand1);
      seed.write(rand2);
      hash_seed = seed.toByteArray();
    } catch (IOException e) {
      LOGGER.error("", e);
    }
    LOGGER.debug("\nhash_seed: "
        + bytesToHexString(hash_seed, 0, hash_seed.length));

    // make first hash secret
    byte[] hash_secret = Arrays.copyOfRange(secret, 0, secret.length / 2
        + secret.length % 2);
    LOGGER.debug("\nhash_secret: "
        + bytesToHexString(hash_secret, 0, hash_secret.length));

    byte[] hash1 = TLS12_hash(hash_secret, hash_seed, Hash.MD5, minSize);
    LOGGER.debug("\nhash1: " + bytesToHexString(hash1, 0, hash1.length));

    // make second hash secret
    hash_secret = Arrays.copyOfRange(secret, hash_secret.length, secret.length);
    LOGGER.debug("\nhash_secret: "
        + bytesToHexString(hash_secret, 0, hash_secret.length));

    byte[] hash2 = TLS12_hash(hash_secret, hash_seed, Hash.SHA, minSize);
    LOGGER.debug("\nhash: " + bytesToHexString(hash2, 0, hash2.length));

    keyMaterial = xor(hash1, hash2);
    LOGGER.debug("\nkeyMaterial: "
        + bytesToHexString(keyMaterial, 0, keyMaterial.length));
    return keyMaterial;
  }

  public static byte[] TLS12_hash(byte[] secret, byte[] hash_seed,
      Hash hash_func, int min_size) {
    if (min_size < 1) {
      return null;
    }

    byte[] A_n_1 = HMAC_hash(secret, hash_seed, hash_func);

    ByteArrayOutputStream temp = new ByteArrayOutputStream(A_n_1.length
        + hash_seed.length);
    ByteArrayOutputStream out = new ByteArrayOutputStream(min_size);

    while (out.size() < min_size) {
      try {
        temp.write(A_n_1);

        temp.write(hash_seed);
        byte[] new_seed = temp.toByteArray();
        temp.reset();

        out.write(HMAC_hash(secret, new_seed, hash_func));

        A_n_1 = HMAC_hash(secret, A_n_1, hash_func);
      } catch (IOException e) {
        LOGGER.error("", e);
      }
    }
    return out.toByteArray();
  }

  public static byte[] HMAC_hash(byte[] secret, byte[] hash_seed, Hash hash_func) {

    byte[] hash = null;
    try {
      MessageDigest digest = MessageDigest.getInstance(hash_func.toString());
      int blocksize = hash_func.blockSize;

      // if secret is larger than the block size, take the hash
      if (secret.length > blocksize) {
        digest.update(secret);
        secret = digest.digest();
        digest.reset();
      }
      if (secret.length < blocksize) {
        byte[] new_secret = new byte[blocksize];
        for (int i = 0; i < secret.length; i++) {
          new_secret[i] = secret[i];
        }
        secret = new_secret;
      }

      byte[] opad = new byte[blocksize];
      Arrays.fill(opad, (byte) 0x5c);

      byte[] ipad = new byte[blocksize];
      Arrays.fill(ipad, (byte) 0x36);

      digest.update(xor(ipad, secret));
      digest.update(hash_seed);

      hash = digest.digest();
      digest.reset();

      digest.update(xor(opad, secret));
      digest.update(hash);

      hash = digest.digest();

    } catch (NoSuchAlgorithmException e) {
      LOGGER.error("", e);
    }
    return hash;
  }

  public static Cipher getCipher(String transformation) {
    Cipher cipher = null;
    try {
      cipher = Cipher.getInstance(transformation);
    } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
      LOGGER.error("", e);
    }
    return cipher;
  }
}
