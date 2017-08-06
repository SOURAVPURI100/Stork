package stork.feather;

import java.io.*;
import java.util.*;

import stork.feather.util.*;

/**
 * A {@code Session} is a stateful context for operating on {@code Resource}s.
 * {@code Session}s serve as a nexus for all of the stateful information
 * related to operating on a particular class of {@code Resource}s, which may
 * include network connections, user credentials, configuration options, and
 * other state necessary for operating on the {@code Resource}s it supports.
 * <p/>
 * {@code Resource}s handled by the {@code Session} may be <i>selected</i>
 * through the {@code Session}, in which case all operations on the {@code
 * Resource} will be performed in the context of the {@code Session}. Whether
 * or not a particular {@code Resource} is capable of being handled by the
 * {@code Session} depends on the URI associated with the {@code Resource} in
 * question.
 * </p>
 * {@code Session}s maintain state information regarding whether or not they
 * are ready for operation, and can be tested for equality based the URI and
 * user credentials used to instantiate them. This makes {@code Session}s
 * suitable for caching and reusing. A {@code Resource} selected on an
 * unconnected {@code Session} can, for instance, be reselected through an
 * equivalent {@code Session} that is still "warm", allowing the {@code
 * Session} to be reused and the initialization overhead to be avoided.
 *
 * @see Resource
 *
 * @param <S> The type of the subclass of this {@code Session}. This
 * unfortunate redundancy exists solely to circumvent weaknesses in Java's
 * typing system.
 * @param <R> The type of {@code Resource}s handled by this {@code Session}.
 */
public abstract class Session<S extends Session<S,R>, R extends Resource<S,R>> {
  /** The URI used to describe this {@code Session}. */
  public final URI uri;

  /** The authentication factor used for this endpoint. */
  public final Credential credential;

  // If we've already started initializing, this will be non-null.
  private volatile Bell initializeBell;

  // Rung on close. Avoid letting this leak out.
  private final Bell<S> onClose = new Bell<S>() {
    public void always() { Session.this.cleanup(); }
  };

  /**
   * Create a {@code Session} with the given root URI.
   *
   * @param uri a {@link URI} representing the root of the {@code Session}.
   * @throws NullPointerException if {@code root} or {@code uri} is {@code
   * null}.
   */
  protected Session(URI uri) { this(uri, null); }

  /**
   * Create a {@code Session} with the given root URI and {@code Credential}.
   *
   * @param uri a {@link URI} describing the {@code Session}.
   * @param credential a {@link Credential} used to authenticate with the
   * endpoint. This may be {@code null} if no additional authentication factors
   * are required.
   * @throws NullPointerException if {@code root} or {@code uri} is {@code
   * null}.
   */
  protected Session(URI uri, Credential credential) {
    this.uri = uri;
    this.credential = credential;
  }

  /**
   * Select a {@code Resource} relative to the root {@code Resource} of this
   * {@code Session} given a {@code String} representation of a {@code Path}.
   *
   * @param path the {@code Path} to the {@code Resource} being selected.
   */
  public final R select(String path) {
    return select(Path.create(path));
  }

  /**
   * Select a {@code Resource} relative to the root {@code Resource} of this
   * {@code Session}.
   *
   * @param path the {@code Path} to the {@code Resource} being selected.
   */
  public abstract R select(Path path);

  /**
   * Return the root {@code Resource} of this {@code Session}.
   *
   * @return The root {@code Resource} of this {@code Session}.
   */
  public final R root() { return select(Path.ROOT); }

  /**
   * This is what actually gets called and returned when {@code
   * Resource.initialize()} is called. It enforces the guarantee that {@code
   * initialize()} will be called only once, and that the same {@code Bell}
   * will always be returned. It will also return a failed {@code Bell} if the
   * {@code Session} is closed.
   */
  final synchronized Bell<S> mediatedInitialize() {
    if (initializeBell != null) {
      return initializeBell;
    } try {
      Bell ib = initialize();
      initializeBell = (ib != null) ? ib : Bell.rungBell();
    } catch (Exception e) {
      initializeBell = new Bell<S>(e);
    } return initializeBell.as(this);
  }

  /**
   * Prepare the {@code Session} to perform operations on its {@code
   * Resource}s. The exact nature of this preparation varies from
   * implementation to implementation, but generally includes establishing
   * network connections and performing authentication. Access to this method
   * is mediated such that the implementor may assume that this method will be
   * called at most once.
   * <p/>
   * Implementations may return {@code null} if {@code resource} does not
   * require any asynchronous initialization. This method may also throw an
   * {@code Exception} if it is certain that initialization cannot be performed
   * for some reason (perhaps invalid input). By default, this method returns
   * {@code null}.
   *
   * @return A {@code Bell} which will ring with this {@code Session} when it
   * is prepared to perform operations on {@code resource}, or {@code null} if
   * the {@code Resource} requires no initialization.
   * @throws Exception either via the returned {@code Bell} or from the method
   * itself if a problem occurs. Subclasses should only declare {@code throws
   * Exception} in the signature if it actually does so, and should omit it
   * otherwise.
   */
  protected Bell<S> initialize() throws Exception { return null; }

  /**
   * Release any resources allocated during the initialization of this {@code
   * Session}. This method should close any connections and finalize any
   * ongoing transactions.
   */
  protected void cleanup() { }

  protected void finalize() { close(); }

  /**
   * Close this {@code Session} and call {@code finalize()}. {@code
   * initialize()} will never be called after this has been called.
   *
   * @return This {@code Session}.
   */
  public final synchronized S close() { return close(null); }

  /**
   * Close this {@code Session} due to the given {@code reason}. {@code
   * initialize()} will never be called after this has been called.
   *
   * @param reason a {@code Throwable} explaining why the {@code Session} was
   * closed.
   * @return This {@code Session}.
   */
  public final synchronized S close(Throwable reason) {
    if (reason == null)
      reason = new IllegalStateException("Session is closed.");
    if (initializeBell != null)
      initializeBell.ring(reason);
    initializeBell = new Bell<S>(reason);
    onClose.ring((S) this);
    return (S) this;
  }

  /**
   * Promise to close this channel when {@code bell} rings.
   *
   * @param bell the {@code Bell} to promise the closing of this channel to.
   */
  public final synchronized void closeWhen(Bell bell) {
    bell.new Promise() {
      public void done()            { close(); }
      public void fail(Throwable t) { close(t); }
    };
  }

  /**
   * Check if the closing procedure has begun.
   *
   * @return {@code true} if closing has begun; {@code false} otherwise.
   */
  public final synchronized boolean isClosed() {
    return onClose.isDone();
  }

  /**
   * Return a bell that will be rung with this {@code Session} when the {@code
   * Session} is closed.
   *
   * @return A {@code Bell} that will be rung when the {@code Session} is
   * closed.
   */
  public final Bell<S> onClose() {
    return onClose.new Promise();
  }

  /**
   * Register a {@code Bell} to be rung with this {@code Session} when the
   * {@code Session} is closed. This is slightly more memory-efficient than
   * promising on the {@code Bell} returned by {@link #onClose()}, as it
   * gets promised to an internal {@code Bell} directly.
   *
   * @param bell a {@code Bell} that will be rung when the {@code Session} is
   * closed.
   * @return Whatever value was passed in for {@code bell}.
   */
  public final Bell<? super S> onClose(Bell<? super S> bell) {
    return onClose.promise(bell);
  }

  public String toString() {
    return uri.toString();
  }

  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Session)) return false;
    Session s = (Session) o;
    if (!uri.equals(s.uri))
      return false;
    if (credential == null)
      return s.credential == null;
    return credential.equals(s.credential);
  }

  public int hashCode() {
    return 1 + 13*uri.hashCode() +
           (credential != null ? 17*credential.hashCode() : 0);
  }
}
