package stork.staging;

import java.util.*;

/** Include dropbox sdk. */
import com.dropbox.core.*; 

import stork.core.*;
import stork.cred.*;

/** OAuth wrapper for Dropbox. */
public class DbxOAuthSession extends OAuthSession {
  /** The URI to go to to finish authentication. */
  final static String finishURI;
  /** Dropbox secret keys. Set from config entries. */
  final static DbxAppInfo secrets;

  public static class DropboxConfig {
    public String key, secret, redirect;
  };

  // Get Dropbox secrets from global config.
  static {
    DropboxConfig c = Config.global.dropbox;
    if (c != null && c.key != null && c.secret != null && c.redirect != null) {
      secrets = new DbxAppInfo(c.key, c.secret);
      finishURI = c.redirect;
    } else {
      secrets = null;
      finishURI = null;
    }
  }

  /** Created after start() is called. */
  private DbxWebAuth auth;
  /** Used by Dropbox SDK. Should be set to user's locale, not ours. */
  private DbxRequestConfig config =
    new DbxRequestConfig("StorkCloud", Locale.getDefault().toString());

  /**
   * Used by Dropbox SDK to store the session key. The "key" member is part of
   * the OAuthSession base class.
   */
  private DbxSessionStore sessionStore = new DbxSessionStore() {
    public void clear() { set(null); }
    public String get() { return key; }
    public void set(String s) { key = s; }
  };

  /** Start the handshake. Return Dropbox OAuth URL. */
  public synchronized String start() {
    if (secrets == null) {
      throw new RuntimeException("Dropbox OAuth is disabled.");
    } if (auth != null) {
      throw new IllegalStateException("Don't call this twice.");
    } try {
      auth = new DbxWebAuth(config, secrets, finishURI, sessionStore);
      return auth.start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** Finish the handshake. */
  public synchronized StorkOAuthCred finish(String token) {
    // Do this to appease the Dropbox SDK.
    Map<String,String[]> map = new HashMap<String,String[]>();
    map.put("state", new String[] {this.key});
    map.put("code", new String[] {token});

    try {
      DbxAuthFinish finish = auth.finish(map);
      StorkOAuthCred cred = new StorkOAuthCred(finish.accessToken);
      cred.name = "Dropbox";
      return cred;
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
}
