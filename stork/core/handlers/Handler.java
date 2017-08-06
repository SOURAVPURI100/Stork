package stork.core.handlers;

import stork.ad.*;
import stork.core.server.*;

/**
 * Stork command handlers should extend this class.
 *
 * @param <R> the type of {@code Request} this handler handles.
 */
public abstract class Handler<R extends Request> {
  public Server server;

  /** Handle the given request. */
  public abstract void handle(R request);

  /**
   * Return a new request form to be filled out. By default, this attempts to
   * guess the type of the request object from the generic type.
   */
  public final R requestForm(String command) {
    R r = newRequestForm();
    r.handler = this;
    r.server = server;
    r.command = command;
    return r;
  }

  /** Override this to use a custom form constructor. */
  protected R newRequestForm() {
    return (R) new AdType(getClass()).superclass().generics()[0].construct();
  }
}
