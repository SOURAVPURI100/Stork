package stork.core.handlers;

import java.util.*;

import stork.core.server.*;
import stork.feather.*;

/** Handles retrieving files. */
public class GetHandler extends Handler<SharedEndpointRequest> {
  public void handle(final SharedEndpointRequest req) {
    req.assertLoggedIn();

    final Resource resource = req.user().sessions.take(req.resolve());
    Transfer t = resource.transferTo(req.resource);
    t.start();
    t.onStop().new Promise() {
      public void done() {
        req.ring();
      } public void fail(Throwable t) {
        req.ring(t);
      } public void always() {
        req.user().sessions.put(resource.session);
      }
    };
  }
}

/** Request for either an endpoint or a shared endpoint. */
class SharedEndpointRequest extends EndpointRequest {
  /** UUID for shared endpoints. */
  public UUID uuid;

  private EndpointRequest findActualEndpoint(String name) {
    if (name == null)
      name = "";
    EndpointRequest req = server().findSharedEndpoint(uuid);
    if (req == null)
      throw new RuntimeException("Invalid share ID for "+name+"endpoint.");
    return req;
  }

  public EndpointRequest validateAs(String name) {
    if (uuid == null)
      return super.validateAs(name);
    return findActualEndpoint(name).validateAs(name);
  }

  public Resource<?,?> resolveAs(String name) {
    if (uuid == null)
      return super.resolveAs(name);
    return findActualEndpoint(name).resolveAs(name);
  }
}

