package org.wayfair.wiff.util.ssl;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.HashMap;
import javax.crypto.Cipher;

import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.wayfair.wiff.core.Wiff;

import static org.wayfair.wiff.util.ByteArrayFunctions.*;
import static org.wayfair.wiff.util.ssl.SSLFunctions.*;

public class SSLDecryptor {
  private static HashMap<BigInteger, byte[]>    map            = new HashMap<BigInteger, byte[]>();
  private static HashMap<String, RSAPrivateKey> privateKeys    = new HashMap<String, RSAPrivateKey>();

  private byte[][]                              random         = new byte[2][];
  private boolean[]                             encrypted      = new boolean[2];
  private boolean                               helloRequested = false;
  private byte[]                                encrypted_premaster;

  private byte[]                                sessionID;

  private byte[]                                nextCipher;

  private KeyExpansion                          keyExpansion, nextKeyExpansion;

  protected static final Logger                 LOGGER         = Logger
                                                                   .getLogger(SSLDecryptor.class);

  static {
    Security.addProvider(new BouncyCastleProvider());

    // try to get the private key file from the config
    String privateKeyFile = Wiff.getRSAPrivateKeyFile();
    if (privateKeyFile != null && !privateKeyFile.isEmpty()) {
      if (privateKeyFile.endsWith(".key")) {
        // File is a single key
        addPrivateKey("*", privateKeyFile);
      } else {
        // File is a mapping of IPs to key files
        try {
          BufferedReader br = new BufferedReader(new FileReader(privateKeyFile));

          String line = br.readLine();
          while (line != null) {
            int split = line.indexOf(" ");
            if (split > 7) {
              String ip = line.substring(0, split);
              String filePath = line.substring(split + 1, line.length());
              addPrivateKey(ip, filePath);
            }
            line = br.readLine();
          }
          br.close();
        } catch (IOException e) {
          LOGGER.error("", e);
        }
      }
    }
  }

  public SSLDecryptor() {
  }

  public SSLDecryptor(String privateKeyFile) {
    // Overwrite any default private key retrieved from the config
    addPrivateKey("*", privateKeyFile);
  }

  public byte[] process(byte[] payload, boolean isClient, String serverIP) {
    if (privateKeys.isEmpty()) {
      LOGGER.error("No private key has been provided.");
      return payload;
    }

    if (!privateKeys.containsKey(serverIP) && !privateKeys.containsKey("*")) {
      LOGGER.error("There is no default private key nor one for " + serverIP);
      return payload;
    }

    ByteArrayOutputStream output = null;
    int record_offset = 0;
    int record_length = 0;
    int record_header_length = 0;
    int record_major_version = 0;
    int record_minor_version = 0;

    while (record_offset >= 0 && record_offset + 5 < payload.length) {
      record_major_version = bytesToInt(payload, record_offset + 1, 1);
      record_minor_version = bytesToInt(payload, record_offset + 2, 1);
      record_length = bytesToInt(payload, record_offset + 3, 2);
      record_header_length = 5;

      // Collect info for debugging
      LOGGER.debug("\n\nSSL Record \nrecord_major_version: "
          + record_major_version + "\nrecord_minor_version: "
          + record_minor_version + "\nrecord_length: " + record_length);

      switch (payload[record_offset]) {
        case CONTENT_Handshake:
          process_Handshake(payload, record_offset + record_header_length,
              record_length, isClient, record_minor_version, serverIP);
          break;
        case CONTENT_AppData:
          try {
            if (output == null) {
              output = new ByteArrayOutputStream(payload.length);
            }
            byte[] appData = process_AppData(payload, record_offset
                + record_header_length, record_length, isClient);
            if (appData != null) {
              output.write(appData);
            }
          } catch (IOException e) {
            LOGGER.error("", e);
          }
          break;
        case CONTENT_ChangeCipherSpec:
          keyExpansion = nextKeyExpansion;
          encrypted[getIndex(isClient)] = true;
          break;
        default:
          // check for SSL2 ClientHello
          if ((payload[record_offset + 2] == 0x01)
              && (payload[record_offset] & 0x80) == 0x80) {

            record_major_version = 2;
            record_minor_version = 0;
            record_header_length = 3;
            record_length = (payload[record_offset] & 0x7f) << 8
                | payload[record_offset + 1];

            random[0] = process_SSL2ClientHello(payload, record_offset);

            // Collect info for debugging
            LOGGER.debug("\n\nSSL Record \nrecord_major_version: "
                + record_major_version + "\nrecord_minor_version: "
                + record_minor_version + "\nrecord_length: " + record_length);

          }
          break;
      }
      // move offset to next record
      record_offset += record_length + record_header_length;
    }

    if (output == null) {
      return null;
    }
    return output.toByteArray();
  }

  private void process_Handshake(byte[] data, int offset, int length,
      boolean isClient, int minorVersion, String serverIP) {
    /*
     * If we have not requested a clienthello, but received one, then it
     * probably isn't encrypted
     */
    if (!helloRequested && data[offset] == HS_ClientHello) {
      encrypted[0] = false;
      encrypted[1] = false;
    }

    int index = getIndex(isClient);
    byte[] bytes = data;

    // Decrypt if encrypted
    if (encrypted[index] && keyExpansion != null) {
      bytes = keyExpansion.decrypt(data, offset, length, isClient);
      offset = 0;
    }

    byte handshake_type = bytes[offset];
    switch (handshake_type) {
      case HS_ClientHello:
        helloRequested = false;
        random[0] = process_ClientHello(bytes, offset);
        break;
      case HS_ServerHello:
        random[1] = process_ServerHello(bytes, offset);
        if (encrypted_premaster != null) {
          byte[] master_secret = map.get(new BigInteger(sessionID));
          if (master_secret != null && random[0] != null && random[1] != null) {
            nextKeyExpansion = new KeyExpansion(nextCipher, master_secret,
                random[0], random[1], minorVersion);
          }
        }
        break;
      case HS_Certificate:
        // process_Certificate(bytes, offset);
        break;
      case HS_ClientKeyExchng:
        encrypted_premaster = process_ClientKeyExchange(bytes, offset,
            minorVersion);

        // Try to get a key for this specific IP
        RSAPrivateKey privateKey = privateKeys.get(serverIP);

        // If no key is found, check for a default key
        if (privateKey == null) {
          privateKey = privateKeys.get("*");
        }

        if (encrypted_premaster != null) {
          Cipher c = getCipher("RSA/ECB/PKCS1Padding");
          byte[] master_secret = generateMasterSecret(encrypted_premaster,
              random[0], random[1], c, privateKey, minorVersion, serverIP);

          if (master_secret != null && random[0] != null && random[1] != null) {
            LOGGER.debug("master_secret"
                + bytesToHexString(master_secret, 0, master_secret.length));
            if (sessionID != null && sessionID.length > 0) {
              map.put(new BigInteger(sessionID), master_secret);
            }
            nextKeyExpansion = new KeyExpansion(nextCipher, master_secret,
                random[0], random[1], minorVersion);
          }
        }
        break;
      case HS_HelloRequest:
        helloRequested = true;
        break;
      case HS_NewSessionTicket:
      case HS_ServerKeyExchng:
      case HS_CertificateReq:
      case HS_ServerHelloDone:
      case HS_CertificateVer:
      case HS_Finished:
        break;
      default:
        break;
    }
  }

  private byte[] process_AppData(byte[] data, int offset, int length,
      boolean isClient) {
    if (keyExpansion == null) {
      return null;
    }
    return keyExpansion.decrypt(data, offset, length, isClient);
  }

  private byte[] process_SSL2ClientHello(byte[] data, int offset) {
    int client_rand_offset = offset + 11 + bytesToInt(data, offset + 5, 2)
        + bytesToInt(data, offset + 7, 2);
    int rand_len = bytesToInt(data, offset + 9, 2);

    byte[] client_rand = new byte[32];
    for (int i = 0; i < rand_len; i++) {
      client_rand[32 - rand_len + i] = data[client_rand_offset + i];
    }
    LOGGER.debug("\nClient Hello Message \nClient Random: "
        + bytesToHexString(client_rand, 0, 32));

    return client_rand;
  }

  private byte[] process_ClientHello(byte[] data, int offset) {
    return Arrays.copyOfRange(data, offset + 6, offset + 38);
  }

  private byte[] process_ServerHello(byte[] payload, int offset) {
    int msg_length = bytesToInt(payload, offset + 1, 3);
    int msg_major_version = bytesToInt(payload, offset + 4, 1);
    int msg_minor_version = bytesToInt(payload, offset + 5, 1);

    // Get server's random number
    byte[] server_rand = Arrays.copyOfRange(payload, offset + 6, offset + 38);

    int session_id_length = bytesToInt(payload, offset + 38, 1);
    sessionID = Arrays.copyOfRange(payload, offset + 39, offset + 39
        + session_id_length);
    String session_id = bytesToHexString(sessionID, 0, session_id_length);

    // Get cipher name
    int cipherOffset = offset + 39 + session_id_length;
    nextCipher = Arrays.copyOfRange(payload, cipherOffset, cipherOffset + 2);

    int compression_method = bytesToInt(payload, offset + 39
        + session_id_length + 2, 1);

    LOGGER.debug("\n\nServer Hello Message \nmsg_length: " + msg_length
        + "\nmsg_major_version: " + msg_major_version + "\nmsg_minor_version: "
        + msg_minor_version + "\nrandom bytes: "
        + bytesToHexString(server_rand, 0, server_rand.length)
        + "\nsession_id_length: " + session_id_length + "\nsession_id: "
        + session_id + "\ncipher: " + bytesToHexString(nextCipher, 0, 2)
        + "\ncompression_method: " + compression_method);

    return server_rand;
  }

  private byte[] process_Certificate(byte[] payload, int offset) {
    int msg_length = bytesToInt(payload, offset + 1, 3);
    int certificates_length = bytesToInt(payload, offset + 4, 3);

    int certificate_length = bytesToInt(payload, offset + 7, 3);
    byte[] publicKey = null;

    try {
      // certificate sequence
      ASN1Sequence seq = new ASN1Sequence(payload, offset + 10);
      // step into certificate
      seq.stepIntoASN1Object();

      // skip signed certificate
      seq.skipASN1Object();

      // skip algorithm identifier
      seq.skipASN1Object();

      // extract public key
      publicKey = seq.extractANS1Object(true);
    } catch (ASN1FormatException e) {
      LOGGER.error("", e);
    }

    LOGGER.debug("\n\nCertificate \nmsg_length: " + msg_length
        + "\ncertificates_length: " + certificates_length
        + "\ncertificate_length: " + certificate_length + "\npublic key: "
        + bytesToHexString(publicKey, 0, publicKey.length));
    return publicKey;
  }

  private byte[] process_ClientKeyExchange(byte[] payload, int offset,
      int minorVersion) {
    int msg_length = bytesToInt(payload, offset + 1, 3);
    int start = offset + 4;
    int length = msg_length;
    if (minorVersion > 0) {
      length = bytesToInt(payload, start, 2);
      start += 2;
    }
    if (payload.length < start + length) {
      return null;
    }

    LOGGER.debug("\n\nClient Key Exchange" + "\nmsg_length: " + msg_length
        + "\npremaster key: " + bytesToHexString(payload, start, length));

    return Arrays.copyOfRange(payload, start, start + length);
  }

  private int getIndex(boolean isClient) {
    return isClient ? 0 : 1;
  }

  private static void addPrivateKey(String ip, String privateKeyFile) {
    if (privateKeyFile != null) {
      try {
        RSAPrivateKey privateKey = readPrivateKeyFile(privateKeyFile);
        privateKeys.put(ip, privateKey);
      } catch (IOException e) {
        LOGGER.error("", e);
      }
    }
  }
}
