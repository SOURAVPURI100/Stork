package stork.cred;

import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

import stork.ad.*;

/** An encrypted username and password. */
public class StorkUserinfo extends StorkCred<String[]> {
  private byte[] username;
  private byte[] password;
  private byte[] key;

  /** Used to cache an unwrapped key. */
  private transient Key realKey;

  /** Used for serialization. */
  protected StorkUserinfo() { super("userinfo"); }

  /**
   * Create a StorkUserinfo with the given username and password and a random
   * key.
   */
  public StorkUserinfo(String username, String password) {
    this(username, password, generateKey());
  }

  /**
   * Create a StorkUserinfo with the given username, password, and key.
   */
  public StorkUserinfo(String username, String password, byte[] key) {
    this();
    this.key = key;
    this.username = encrypt(username);
    this.password = encrypt(password);
  }

  /** Decrypt and return the username and password. */
  public String[] data() {
    return new String[] { decrypt(username), decrypt(password) };
  }

  /** Decrypt and return the username. */
  public String username() { return decrypt(username); }

  /** Decrypt and return the password. */
  public String password() { return decrypt(password); }

  /** Return a user/pass pair from a colon-separated string. */
  public static String[] split(String ui) {
    String u = null, p = null;
    if (ui != null && !ui.isEmpty()) {
      int i = ui.indexOf(':');
      u = (i < 0) ? ui : ui.substring(0,i);
      p = (i < 0) ? "" : ui.substring(i+1);
    } return new String[] { u, p };
  }

  /** Get an uninitialized cipher. */
  private static Cipher cipher() throws Exception {
    return Cipher.getInstance("AES/CBC/PKCS5Padding");
  }

  /** Get the key. */
  private Key key() {
    if (key == null) {
      throw new RuntimeException("Credential has no key.");
    } if (realKey == null) try {
      Cipher cipher = cipher();
      return new SecretKeySpec(key, "AES");
    } catch (Exception e) {
      throw new RuntimeException(e);
    } return realKey;
  }

  /** Generate a key to be used to encrypt/decrypt. */
  private static byte[] generateKey() {
    try {
      KeyGenerator keygen = KeyGenerator.getInstance("AES");
      keygen.init(128);
      Key key = keygen.generateKey();
      return key.getEncoded();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** Encrypt a string using the key. */
  private byte[] encrypt(String s) {
    try {
      byte[] bytes = s.getBytes();
      Cipher cipher = cipher();
      IvParameterSpec iv = new IvParameterSpec(new byte[16]);
      cipher.init(Cipher.ENCRYPT_MODE, key(), iv);
      return cipher.doFinal(bytes);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** Decrypt bytes into a string using the key. */
  private String decrypt(byte[] bytes) {
    try {
      Cipher cipher = cipher();
      IvParameterSpec iv = new IvParameterSpec(new byte[16]);
      cipher.init(Cipher.DECRYPT_MODE, key(), iv);
      return new String(cipher.doFinal(bytes));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected Object[] hashables() {
    return data();
  }
}
