package stork.core.handlers;

import stork.core.server.*;
import stork.feather.*;

/** Request common to many commands that operate on endpoints. */
public class EndpointRequest extends Request {
  public String uri;
  public InnerCredRequest credential;
  public String module;

  // Hack to get around marshalling badness.
  private class InnerCredRequest extends CredRequest {
    public User user() { return EndpointRequest.this.user(); }
  }

  /** Get the {@code Resource} identified by this request. */
  public Resource resolve() { return resolveAs(null); }

  /**
   * Get the {@code Resource} identified by this request using {@code name} in
   * error messages.
   */
  public Resource resolveAs(String name) {
    return validateAndResolve(name).resolve();
  }

  /** Validate the request. */
  public EndpointRequest validate() { return validateAs(null); }

  /** Validate the request using {@code name} in error messages. */
  public EndpointRequest validateAs(String name) {
    validateAndResolve(name);
    return this;
  }

  // This method might be doing too much, but it's useful to have this in one
  // place.
  private RealEndpoint validateAndResolve(String name) {
    name = (name == null) ? "" : name+" ";
    RealEndpoint result = new RealEndpoint();

    if (uri != null)
      result.uri = URI.create(uri);  // Takes care of syntax errors.
    if (result.uri == null)
      throw new RuntimeException("No URI provided for "+name+"endpoint.");
    if (result.uri.scheme() == null)
      throw new RuntimeException("No URI scheme for "+name+"endpoint.");
    if (credential != null)
      result.credential = credential.resolve();
    if (credential != null && result.credential == null)
      throw new RuntimeException("Invalid credential for "+name+"endpoint.");
    if (module != null)
      result.module = server().modules.byHandle(module);
    else
      result.module = server().modules.byProtocol(result.uri.scheme());
    return result;
  }

  /** This may be overridden by subclasses. */
  public Server server() { return server; }

  /** This may be overridden by subclasses. */
  public User user() { return super.user(); }
}

// This is really just a holder for three objects.
class RealEndpoint {
  URI uri;
  Credential credential;
  stork.module.Module module;

  public Resource resolve() {
    return module.select(uri, credential);
  }
}
