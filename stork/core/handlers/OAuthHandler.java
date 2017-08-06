package stork.core.handlers;

import java.util.*;

import stork.core.server.*;
import stork.cred.*;
import stork.feather.errors.*;
import stork.staging.*;

public class OAuthHandler extends Handler<OAuthRequest> {
  /** Used to hold ongoing sessions. */
  private static Map<String,OAuthSession> sessions =
    new HashMap<String,OAuthSession>();

  public void handle(final OAuthRequest req) {
    req.assertLoggedIn();

    // Request state will only be given when we're finishing a handshake, so
    // use its presense to determine whether we're starting or finishing a
    // handshake.
    if (req.state == null)
      handleStart(req);
    else
      handleFinish(req);

    // If we get here, it's because we didn't redirect the user anywhere. Just
    // send them home, I guess.
    throw new Redirect("/");
  }

  /**
   * This is called when we're starting an OAuth handshake. It should redirect
   * the user to the OAuth URL.
   */
  private void handleStart(OAuthRequest req) {
    OAuthSession session = newSession(req.type);
    String url = session.start();

    // The URL should actually never be null, but just in case...
    if (url != null) {
      storeSession(session.key, session);
      throw new Redirect(url);
    }
  }

  /** This is called when we're finishing an OAuth handshake. */
  private void handleFinish(OAuthRequest req) {
    if (req.code == null)
      throw new RuntimeException("Missing OAuth token.");

    OAuthSession session = findSession(req.state);

    if (session == null)
      throw new RuntimeException("Invalid session key.");

    StorkOAuthCred cred = session.finish(req.code);
    String uuid = req.user().addCredential(cred);
    server.dumpState();

    throw new Redirect("/oauth/"+uuid);
  }

  /** Create a new OAuthSession of a given type. */
  private OAuthSession newSession(String type) {
    if ("dropbox".equals(type))
      return new DbxOAuthSession();
    throw new RuntimeException("Expecting OAuth type.");
  }

  /** Save an OAuthSession so we can recover it later. */
  private static synchronized
  void storeSession(String key, OAuthSession session) {
    sessions.put(key, session);
  }

  /** Recover a saved session. */
  private static synchronized OAuthSession findSession(String key) {
    return sessions.get(key);
  }
}

class OAuthRequest extends Request {
  String type, state, code;

  Map<String,String[]> toParamMap() {
    Map<String,String[]> map = new HashMap<String,String[]>();
    map.put("state", new String[] {state});
    map.put("code", new String[] {code});
    return map;
  }
}
