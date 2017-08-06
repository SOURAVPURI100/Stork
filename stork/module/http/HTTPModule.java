package stork.module.http;

import stork.feather.*;
import stork.module.*;

public class HTTPModule extends Module<HTTPResource> {
  {
    name("Stork HTTP Module");
    protocols("http", "https");
    description("A module for interacting with HTTP(S) resources.");
  }

  public HTTPResource select(URI uri, Credential credential) {
    URI ep = uri.endpointURI(), res = uri.resourceURI();
    //return new HTTPSession(ep).select(res.path(), res.query());
    return new HTTPSession(ep).select(res.path());
  }
}
