package stork.core.server;

import java.util.*;

import stork.ad.*;
import stork.core.handlers.*;
import stork.feather.*;
import stork.feather.errors.*;

/**
 * A request made to the scheduler. Handlers should provide an instance of a
 * subclass of {@code Request} via the {@link Handler#requestForm(String)}
 * method containing additional fields the requestor may supply. When the
 * request has been handled, the handler should call {@code ring()} with an
 * optional value that will be marshalled and sent to the requestor to complete
 * the request.
 */
public abstract class Request extends Bell<Object> implements Runnable {
  /** The command given with the request. */
  public String command;

  /** The requested API version. Currently ignored. */
  public String version;

  /** The handler which will handle this request. */
  public transient Handler handler;

  private transient User user;

  private transient RequestCookie cookie;

  private class RequestCookie extends User.Cookie {
    public Server server() { return Request.this.server; }
  }

  /** The server this request was made to. */
  public transient Server server;

  /** Whether or not the handler is allowed to change state. */
  public transient boolean mayChangeState = false;

  /** A resource representing a communication channel with the requestor. */
  public transient Resource resource;

  /** The original unmarshalled request. */
  private transient Ad ad = new Ad();

  /** Marshal this request into another request. */
  public <R extends Request> R marshalInto(R request) {
    Ad ad = Ad.marshal(this).merge(this.ad);

    request.command = command;
    request.server = server;
    request.cookie(cookie);
    request.mayChangeState = mayChangeState;
    request.resource = resource;

    return (R) request.unmarshalFrom(ad);
  }

  /** Get the user associated with the request. */
  public User user() {
    if (cookie == null) {
      user = server.anonymous;
    } if (user == null) try {
      user = cookie.login();
    } catch (Exception e) {
      user = server.anonymous;
    } return user;
  }

  /** Marshal some object into a cookie. */
  public void cookie(Object cookie) {
    if (cookie != null)
      this.cookie = Ad.marshal(cookie).unmarshal(new RequestCookie());
    else
      this.cookie = null;
    user = null;
  }

  /** Marshal an object into this request. */
  public Request unmarshalFrom(Object object) {
    ad = ad.merge(Ad.marshal(object));
    return ad.unmarshal(this);
  }

  /** Handle the request. */
  public void handle() {
    try {
      handler.handle(this);
    } catch (Exception e) {
      ring(e);
    }
  }

  /** This just calls handle(). */
  public void run() { handle(); }

  public Ad asAd() {
    return Ad.marshal(this).merge(ad);
  }

  public void assertLoggedIn() {
    if (!server.config.registration)
      return;
    if (user() == null || user().isAnonymous())
      throw new PermissionDenied();
    if (!user().validated)
      throw new User.NotValidatedException();
  }

  public void assertMayChangeState() {
    if (!mayChangeState)
      throw new PermissionDenied();
  }
}
