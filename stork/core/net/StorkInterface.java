package stork.core.net;

import java.net.*;

import stork.ad.*;
import stork.core.server.*;
import stork.feather.*;
import stork.feather.URI;
import stork.feather.errors.*;

/**
 * An interface which awaits incoming client requests and passes them on to the
 * server.
 */
public abstract class StorkInterface {
  private final Server server;

  /**
   * Create a {@code StorkInterface} for the given server.
   *
   * @param server the server to provide an interface for.
   */
  public StorkInterface(Server server) {
    this.server = server;
  }

  /**
   * Automatically create an interface from a URI.
   *
   * @return A {@code StorkInterface} listening for connection at endpoint
   * specified by {@code uri}.
   * @param server the server to provide an interface for.
   * @param uri the URI specifying the interface to listen on.
   * @throws RuntimeException if an interface could not be created based on the
   * URI.
   */
  public static StorkInterface create(Server server, URI uri) {
    String proto = uri.scheme();

    if (proto == null)
      throw new RuntimeException("Invalid interface descriptor: "+uri);
    if (proto.equals("tcp"))
      return new TCPInterface(server, uri);
    if (proto.equals("http") || proto.equals("https"))
      return new HTTPInterface(server, uri);
    throw new RuntimeException(
      "Unsupported interface scheme ("+proto+") in "+uri);
  }

  /** Get the name of this interface. This is used for logging purposes. */
  public abstract String name();

  /**
   * Get a description of the address this interface is listening on. This is
   * used for logging purposes.
   */
  public abstract String address();

  /** Get a new request form for the given command. */
  protected Request getRequestForm(String command) {
    return server.getRequestForm(command);
  }

  /** Issue a request to the server. */
  protected Request issueRequest(Request request) {
    return server.issueRequest(request);
  }

  /**
   * Create an ad representing a {@code Throwable}.
   *
   * @param throwable a {@code Throwable} to return an ad representing.
   * @return An ad representing {@code throwable}.
   */
  public static Ad errorToAd(final Throwable throwable) {
    if (throwable == null) {
      return errorToAd(new NullPointerException());
    } return Ad.marshal(new Object() {
      String type = throwable.getClass().getSimpleName();
      String error = message(throwable);

      // This is a quick hack to deal with authentication options.
      String[] options = (throwable instanceof AuthenticationRequired) ?
        ((AuthenticationRequired) throwable).options : null;

      String message(Throwable t) {
        if (t == null) return "(no reason given)";
        String m = t.getLocalizedMessage();
        return m != null ? m : message(t.getCause());
      }
    });
  }
}
