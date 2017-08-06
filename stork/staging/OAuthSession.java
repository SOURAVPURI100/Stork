package stork.staging;

import stork.cred.*;

/**
 * Experimental holder for OAuth session data.
 */
public abstract class OAuthSession {
  /**
   * The session token used for this handshake. This is set after start is
   * called. Can be used as a key in a session map.
   */
  public String key;

  /** Start the handshake. Set {@code session} and return OAuth URL. */
  public abstract String start();

  /** Finish the handshake. */
  public abstract StorkOAuthCred finish(String token);
}
