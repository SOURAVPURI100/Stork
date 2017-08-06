package stork.core.handlers;

import java.util.*;

import stork.core.server.*;
import stork.feather.*;

/** A handler for creating shared endpoints. */
public class ShareHandler extends Handler<ShareRequest> {
  public void handle(final ShareRequest req) {
    req.assertLoggedIn();
    req.assertMayChangeState();

    Resource<?,?> resource = req.resolve();

    // Make sure the resource exists and is a file.
    resource.stat().new As<UUID>() {
      public UUID convert(Stat stat) {
        if (!stat.file)
          throw new RuntimeException("Resource must be a file.");
        return req.createShare();
      }
    }.new As<Object>() {
      public Object convert(final UUID u) {
        return new Object() { UUID uuid = u; };
      }
    }.promise(req);
  }
}

class ShareRequest extends EndpointRequest {
  UUID createShare() {
    return server().createSharedEndpoint(user(), this);
  }
}
