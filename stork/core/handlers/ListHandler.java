package stork.core.handlers;

import java.util.*;
import java.util.concurrent.*;

import stork.core.server.*;
import stork.feather.*;
import stork.util.*;

/** A handler for performing listings. */
public class ListHandler extends Handler<ListRequest> {
  // Map of ongoing listings, for request aggregation.
  private static Map<Resource, Bell<Stat>> aggregator =
    new ConcurrentHashMap<Resource, Bell<Stat>>();

  public void handle(final ListRequest req) {
    req.assertLoggedIn();

    final Resource resource;
    Bell<Stat> listing = null;

    Log.info("Taking session out...");
    if (!req.forceRefresh)
      resource = req.user().sessions.take(req.resolve());
    else
      resource = req.resolve();

    if (!req.forceRefresh)
      listing = aggregator.get(resource);

    if (listing != null) {
      Log.fine("Waiting on existing list request...");
      listing.promise(req);
      return;
    }

    listing = resource.stat();

    // Register the ongoing listing.
    aggregator.put(resource, listing);
    listing.new Promise() {
      public void always() {
        aggregator.remove(resource);
      }
    };

    // Put the session back when we're done.
    listing.new Promise() {
      public void always() {
        Log.info("Putting session back...");
        req.user().sessions.put(resource.session);
        aggregator.remove(resource);
      }
    };

    listing.promise(req);
  }
}

// A listing request is just an endpoint request with some options.
class ListRequest extends EndpointRequest {
  boolean forceRefresh = false;
}
