package stork.core.server;

import java.util.*;

import stork.feather.*;
import stork.util.*;

public class SessionCache {
  private Map<Session,Session> map = new HashMap<Session, Session>();

  public synchronized Resource take(Resource resource) {
    Session session = take(resource.session);
    if (resource.session == session)
      return resource;
    return resource.reselectOn(session);
  }

  public synchronized Session take(Session session) {
    Session cached = map.get(session);
    if (cached == null || cached.isClosed()) {
      Log.info("Using new session: "+session);
      return session;
    }
    map.remove(cached);
    Log.info("Reusing existing session: "+session);
    return cached;
  }

  public synchronized Session put(final Session session) {
    if (session.isClosed())
      return session;
    Session cached = map.get(session);
    if (cached != null)
      return cached;
    session.onClose(new Bell() {
      public void always() { remove(session); }
    });
    map.put(session, session);
    return session;
  }

  public synchronized Session remove(Session session) {
    map.remove(session);
    return session;
  }
}
