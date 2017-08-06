package stork.feather;

import java.util.*;
import java.util.concurrent.*;

import stork.feather.util.*;

/**
 * A promise primitive used for stringing together the results of asynchronous
 * operations and executing asynchronous handlers. All handling operations are
 * performed in a separate dispatch thread. It supports callbacks defined in
 * subclasses, chaining of results, deadlines, and back-cancellation.
 *
 * @param <T> the supertype of objects that can ring this {@code Bell}.
 */
public class Bell<T> implements Future<T> {
  // Statically allocated pre-rung bells.
  private final static Bell rungBell = new Bell((Object) null);
  private final static Bell failedBell = new Bell((Throwable) null);
  private final static Bell cancelledBell = new Bell().cancel();

  /** The object held by this bell, once rung. May contain T or Throwable. */
  private transient Object object;
  private transient List<Bell<? super T>> promises = Collections.emptyList();

  private static final Dispatcher dispatcher =
    new Dispatcher("Bell Dispatcher");

  // State. 0 = unrung, 1 = thenned, 2 = done, 3 = failed
  private transient byte state = 0;

  /** Create an unrung {@code Bell}. */
  public Bell() { }

  /** Create a {@code Bell} rung with {@code object}. */
  public Bell(T object) { ring(object); }

  /** Create a {@code Bell} rung with {@code error}. */
  public Bell(Throwable error) { ring(error); }

  // Get the object as a T.
  private T object() {
    return (isSuccessful()) ? (T) object : null;
  }

  // Get the object as a Throwable.
  private Throwable error() {
    return (isFailed()) ? (Throwable) object : null;
  }

  /**
   * A static {@code Bell} that has already been rung.
   *
   * @return A {@code Bell} that has already been rung with {@code null}.
   */
  public static <C> Bell<C> rungBell() {
    return (Bell<C>) rungBell;
  }

  /**
   * A static {@code Bell} that has already failed.
   *
   * @return A {@code Bell} that has already failed with {@code
   * NullPointerException}.
   */
  public static <C> Bell<C> failedBell() {
    return (Bell<C>) failedBell;
  }

  /**
   * A static {@code Bell} that has been cancelled.
   *
   * @return A {@code Bell} that has already failed with {@code
   * CancellationException}.
   */
  public static <C> Bell<C> cancelledBell() {
    return (Bell<C>) cancelledBell;
  }

  /**
   * Ring the {@code Bell} with {@code object}.
   *
   * @param object The {@code T} to ring the {@code Bell} with.
   * @return This {@code Bell}.
   */
  public final synchronized Bell<T> ring(T object) {
    return ring(object, null);
  }

  /**
   * Ring the {@code Bell} with {@code null}.
   *
   * @return This {@code Bell}.
   */
  public final synchronized Bell<T> ring() {
    return ring(null, null);
  }

  /**
   * Ring the {@code Bell} with {@code error}.
   *
   * @param error The {@code Throwable} to ring the {@code Bell} with.
   * @return This {@code Bell}.
   */
  public final synchronized Bell<T> ring(Throwable error) {
    return ring(null, (error != null) ? error : new NullPointerException());
  }

  /**
   * Used by the other ring methods. If error is not {@code null}, assume
   * failure and ignore {@code object}.
   *
   * @param object The object to ring the {@code Bell} with.
   * @param error The error to ring the {@code Bell} with.
   * @return This {@code Bell}.
   */
  private synchronized Bell<T> ring(T object, Throwable error) {
    if (!isDone()) {
      state = (error == null) ? (byte) 2 : 3;
      this.object = (error == null) ? object : error;
      dispatchHandlers();
      dispatchPromises(promises);
      promises = Collections.emptyList();
      notifyAll();
    } return this;
  }

  /**
   * Dispatch this Bell's handlers. The top-level Bell class has no-op
   * handlers, so only dispatch if this is a subclass of Bell.
   */
  private void dispatchHandlers() {
    if (getClass() != Bell.class) dispatch(new Runnable() {
      public void run() {
        // Call the handlers.
        if (isFailed()) try {
          fail(error());
        } catch (Throwable t) {
          // Discard.
        } else try {
          done(object());
        } catch (Throwable t) {
          // Discard.
        } try {
          always();
        } catch (Throwable t) {
          // Discard.
        }
      }
    });
  }

  /** Dispatch a promise to all promised bells. */
  private <B extends Bell<? super T>> void dispatchPromises(List<B> bells) {
    if (bells.isEmpty())
      return;
    if (isFailed())
      dispatch(new DispatchFail<B>(bells, error()));
    else
      dispatch(new DispatchDone<B,T>(bells, object()));
  }

  /** Dispatch a promise to a single bell. */
  private <B extends Bell<? super T>> void dispatchPromise(B bell) {
    List<B> bells = Collections.singletonList(bell);
    dispatchPromises(bells);
  }

  /**
   * Cancel the {@code Bell}, resolving it with a {@code
   * CancellationException}.
   *
   * @param mayInterruptIfRunning Ignored in this implementation. @return
   * {@code true} if the {@code Bell} was cancelled as a result of this call,
   * {@code false} otherwise.
   * @see Future#cancel(boolean)
   */
  public synchronized boolean cancel(boolean mayInterruptIfRunning) {
    if (isDone())
      return false;
    ring(null, new CancellationException());
    return true;
  }

  /**
   * Cancel the {@code Bell}, resolving it with a cancellation error. This
   * returns a reference to this {@code Bell}, unlike {@link #cancel(boolean)}.
   *
   * @return This {@code Bell}.
   */
  public Bell<T> cancel() {
    cancel(true);
    return this;
  }

  /**
   * @return {@code true} if this {@code Bell} was rung with a {@code
   * CancellationException}.
   * @see Future#isCancelled
   */
  public synchronized boolean isCancelled() {
    return isFailed() && error() instanceof CancellationException;
  }

  /** Return {@code true} if the {@code Bell} is unrung. */
  private synchronized boolean isUnrung() {
    return state == 0;
  }

  /** Return {@code true} if the {@code Bell} has been rung. */
  public final synchronized boolean isDone() {
    return state >= 2;
  }

  /** Return {@code true} if the {@code Bell} rang successfully. */
  public final synchronized boolean isSuccessful() {
    return isDone() && state == 2;
  }

  /** Return {@code true} if the {@code Bell} failed. */
  public final synchronized boolean isFailed() {
    return isDone() && state == 3;
  }

  /** Wait for the {@code Bell} to be rung, then return the value. */
  public synchronized T get() throws InterruptedException, ExecutionException {
    while (!isDone())
      wait();
    return getOrThrow();
  }

  /**
   * Wait for the {@code Bell} to be rung up to the specified time, then return the
   * value.
   */
  public synchronized T get(long timeout, TimeUnit unit)
  throws InterruptedException, ExecutionException, TimeoutException {
    if (!isDone())
      unit.timedWait(this, timeout);
    if (!isDone())
      throw new TimeoutException();
    return getOrThrow();
  }

  /** Either get the object or throw the wrapped error. Only call if done. */
  private T getOrThrow() throws ExecutionException {
    if (!isFailed())
      return object();
    if (error() instanceof CancellationException)
      throw (CancellationException) error();
    if (error() instanceof ExecutionException)
      throw (ExecutionException) error();
    throw new ExecutionException(error());
  }

  /**
   * This is an alternative way of getting the wrapped value that is more
   * convenient for the caller. It blocks uninterruptably and throws unchecked
   * exceptions.
   */
  public synchronized T sync() {
    while (!isDone()) try {
      wait();
    } catch (InterruptedException e) {
      // Ignore it.
    } if (!isFailed()) {
      return object();
    } if (error() instanceof RuntimeException) {
      throw (RuntimeException) error();
    } throw new RuntimeException(error());
  }

  /**
   * This handler is called when this {@code Bell} has rung. Subclasses can
   * override this to do something when the {@code Bell} has been rung. Any
   * exceptions thrown will be discarded. Implement this if you don't care
   * about the value.
   */
  protected void done() throws Throwable { }

  /**
   * This handler is called when this {@code Bell} has rung and is passed the
   * rung value. Subclasses can override this to do something when the {@code
   * Bell} has been rung. Any exceptions thrown will be discarded. Implement
   * this if you want to see the value.
   *
   * @param object the resolution value of this {@code Bell}.
   */
  protected void done(T object) throws Throwable { done(); }

  /**
   * This handler is called when the {@code Bell} has rung with an exception.
   * Subclasses can override this to do something when the {@code Bell} has
   * been rung. Any exceptions thrown will be discarded. Implement this if you
   * don't care about the error.
   */
  protected void fail() throws Throwable { }

  /**
   * This handler is called when the {@code Bell} has rung with an exception,
   * and is passed the exception. Subclasses can override this to do something
   * when the {@code Bell} has been rung. Any exceptions thrown will be
   * discarded. Implement this if you want to see the error.
   *
   * @param error the {@code Throwable} this {@code Bell} failed with.
   */
  protected void fail(Throwable error) throws Throwable { fail(); }

  /**
   * Subclasses can override this to do something when the {@code Bell} has
   * been rung. Any {@code Exception}s thrown will be discarded. This will
   * always be run after either {@link #done(Object)} or {@link
   * #fail(Throwable)}.
   */
  protected void always() throws Throwable { }

  /**
   * Promise to ring another {@code Bell} when this {@code Bell} rings.
   * Promised {@code Bell}s will ring in the order they are promised and after
   * this {@code Bell}'s handlers have been called.
   *
   * @param bell the {@code Bell} to promise to this {@code Bell}.
   * @return The value passed in for {@code bell}.
   */
  public synchronized <V extends Bell<? super T>> V promise(V bell) {
    if (bell.isDone()) {
      return bell;  // Don't be silly...
    } if (isDone()) {
      dispatchPromise(bell);  // We've already rung, dispatch.
    } else switch (promises.size()) {
      case 0:  // This is an immutable empty list. Change to singleton.
        promises = (List) Collections.singletonList(bell);
        break;
      case 1:  // This is the singleton. Make a mutable list.
        promises = new LinkedList<Bell<? super T>>(promises);
      default:
        promises.add(bell);
    } return bell;
  }

  /**
   * Return a new {@code Bell} promised to this {@code Bell}. This has a number
   * of uses, including preventing callers from ringing shared {@code Bell}s
   * and preventing memory leaks.
   */
  public Bell<T> detach() {
    return (Bell<T>) promise(new Bell<T>());
  }

  /**
   * A {@code Bell} which is promised to the parent {@code Bell} on
   * instantiation.
   */
  public class Promise extends Bell<T> {
    { Bell.this.promise(this); }
  }

  /**
   * A {@code Bell} which is promised to the parent {@code Bell} on
   * instantiation, and performs a conversion using the {@code convert(T)}
   * method. This simplifies the {@code Bell} conversion chain pattern.
   *
   * @param <V> the supertype of objects this {@code Bell} converts.
   */
  public abstract class As<V> extends Bell<V> {
    {
      Bell.this.new Promise() {
        public void done(T t) {
          try {
            As.this.ring(convert(t));
          } catch (Throwable e) {
            As.this.ring(e);
          }
        } public void fail(Throwable t) {
          try {
            As.this.ring(convert(t));
          } catch (Throwable e) {
            As.this.ring(e);
          }
        }
      };
    }

    /**
     * Convert the parent {@code Bell}'s ringing object into another type. Any
     * {@code Throwable} thrown here will cause this {@code Bell} to fail with
     * the {@code Throwable}.
     *
     * @param t the object to convert.
     * @throws Throwable an arbitrary {@code Throwable}.
     */
    protected abstract V convert(T t) throws Throwable;

    /**
     * Convert a {@code Throwable} from a failed parent {@code Bell} into
     * another type. Any error thrown here will cause this {@code Bell} to
     * fail with the error. Implementing this is optional, and by default will
     * simply fail this {@code Bell} with the {@code Throwable}.
     *
     * @param t the throwable to convert.
     * @throws Throwable an arbitrary {@code Throwable}.
     */
    protected V convert(Throwable t) throws Throwable { throw t; }
  }

  /**
   * A {@code Bell} which is promised to the parent {@code Bell} on
   * instantiation, and performs a conversion using the {@code convert(T)}
   * method. This simplifies the {@code Bell} conversion chain pattern.
   *
   * @param <V> the supertype of objects this {@code Bell} converts.
   */
  public abstract class AsBell<V> extends Bell<V> {
    {
      Bell.this.new Promise() {
        public void done(T t) {
          try {
            Bell<V> convert = convert(t);
            if (convert != null) convert.promise(AsBell.this);
          } catch (Throwable e) {
            AsBell.this.ring(e);
          }
        } public void fail(Throwable t) {
          try {
            Bell<V> convert = convert(t);
            if (convert != null) convert.promise(AsBell.this);
          } catch (Throwable e) {
            AsBell.this.ring(e);
          }
        }
      };
    }

    /**
     * Convert the parent {@code Bell}'s ringing object into another {@code
     * Bell}. This method may return {@code null} to indicate that some other
     * mechanism (including the method itself) may ring this {@code Bell}. Any
     * {@code Throwable} thrown here will cause this {@code Bell} to fail with
     * the {@code Throwable}.
     *
     * @param t the object to convert.
     * @throws Throwable an arbitrary {@code Throwable}.
     */
    protected abstract Bell<V> convert(T t) throws Throwable;

    /**
     * Convert a {@code Throwable} from a failed parent {@code Bell} into
     * another {@code Bell}. This method may return {@code null} to indicate
     * that some other mechanism (including the method itself) may ring this
     * {@code Bell}. Any error thrown here will cause this {@code Bell} to fail
     * with the error. Implementing this is optional, and by default will
     * simply fail this {@code Bell} with the {@code Throwable}.
     *
     * @param t the throwable to convert.
     * @throws Throwable an arbitrary {@code Throwable}.
     */
    protected Bell<V> convert(Throwable t) throws Throwable { throw t; }
  }

  /**
   * Return a {@code Bell} which will ring with {@code done} when this {@code
   * Bell} rings successfully.
   *
   * @param done the value to ring the returned {@code Bell} with when this {@code
   * Bell} rings.
   */
  public final <V> Bell<V> as(final V done) {
    final Bell<V> bell = new Bell<V>();
    this.new Promise() {
      public void done() { bell.ring(done); }
      public void fail(Throwable t) { bell.ring(t); }
    };
    return bell;
  }

  /**
   * Return a {@code Bell} which will ring with {@code done} if this {@code
   * Bell} rings successfully and {@code fail} if this {@code Bell} fails.
   *
   * @param done the value to ring the returned {@code Bell} with on success.
   * @param fail the value to ring the returned {@code Bell} with on failure.
   */
  public final <V> Bell<V> as(final V done, final V fail) {
    final Bell<V> bell = new Bell<V>();
    this.new Promise() {
      public void done() { bell.ring(done); }
      public void fail() { bell.ring(fail); }
    };
    return bell;
  }

  /**
   * Set the deadline of the {@code Bell} in seconds. The deadline is relative
   * to the time this method is called. If the {@code Bell} is not rung in this
   * time, it will be resolved with a {@link TimeoutException}.
   *
   * @param deadline the time in seconds after call time that the {@code Bell}
   * may remain unresolved.
   */
  public synchronized Bell<T> deadline(double deadline) {
    if (!isDone()) new Task() {
      public void run() { ring(new TimeoutException()); }
    }.dispatch(deadline);
    return this;
  }

  /**
   * Create a {@code Bell} that will ring with {@code null} after {@code
   * deadline} seconds.
   *
   * @param deadline the time in seconds after this call that {@code Bell} will
   * ring.
   */
  public static Bell<?> timerBell(double deadline) {
    final Bell<?> bell = new Bell<Void>();
    dispatch(new Runnable() {
      public void run() { bell.ring(); }
    }, deadline);
    return bell;
  }

  /**
   * @return A {@code Bell} which only rings if this {@code Bell} succeeds, and
   * does not ring otherwise.
   */
  public Bell<T> onSuccess() {
    final Bell<T> bell = new Bell<T>();
    new Promise() {
      public void done(T t) { bell.ring(t); }
    };
    return bell;
  }

  /**
   * @return A {@code Bell} which only rings if this {@code Bell} fails, and
   * does not ring otherwise.
   */
  public <T> Bell<T> onFail() {
    final Bell<T> bell = new Bell<T>();
    new Promise() {
      public void fail(Throwable t) { bell.ring(t); }
    };
    return bell;
  }

  /**
   * Return a {@code Bell} which will ring with either the value of this {@code
   * Bell} or {@code other}. If both fail, the returned {@code Bell} will ring
   * with the error of this {@code Bell}.
   */
  public Bell<T> or(final Bell<T> other) {
    return new AsBell<T>() {
      { other.onSuccess().promise(this); }
      public Bell<T> convert(T t) { return Bell.this; }
      public Bell<T> convert(Throwable t) { return other; }
    };
  }

  /**
   * Return a {@code Bell} which will ring with the value of {@code other} if
   * this {@code Bell} rings successfully, or else will fail if this {@code
   * Bell} fails.
   */
  public <V> Bell<V> and(final Bell<V> other) {
    final Bell<V> bell = new Bell<V>();
    new Promise() {
      public void done(T t) {
        other.promise(bell);
      } public void fail(Throwable t) {
        bell.ring(t);
      }
    };
    other.new Promise() {
      public void done(V v) {
        if (Bell.this.isDone()) bell.ring(v);
      } public void fail(Throwable t) {
        bell.ring(t);
      }
    };
    return bell;
  }

  /**
   * Check if any {@code Bell} rings successfully.
   *
   * @param bells an array of {@code Bell}s to check.
   * @return A {@code Bell} that will ring successfully if any of {@code bells}
   * rings successfully, or will fail otherwise.
   */
  public static Bell<?> any(Bell<?>... bells) {
    return any(Arrays.asList(bells));
  }

  /**
   * Check if any {@code Bell} rings successfully.
   *
   * @param bells a {@code Collection} of {@code Bell}s to check.
   * @return A {@code Bell} that will ring successfully if any of {@code bells}
   * rings successfully, or will fail otherwise.
   */
  public static Bell<?> any(final Collection<Bell<?>> bells) {
    final int len = bells.size() - Collections.frequency(bells, null);
    if (len == 0)
      return Bell.rungBell();
    if (len == 1) for (Bell b : bells)
      if (b != null) return b;
    return new Bell() {
      int failed = 0;
      {
        final Bell bell = this;
        for (final Bell b : bells) if (b != null) b.new Promise() {
          public void done() {
            bell.ring();
          } public void fail(Throwable t) {
            if (++failed == len) bell.ring(t);
          }
        };
      }
    };
  }

  /**
   * Check if all {@code Bell}s ring successfully.
   *
   * @param bells an array of {@code Bell}s to check.
   * @return A {@code Bell} that will ring successfully if all of {@code bells}
   * ring successfully, or will fail otherwise.
   */
  public static Bell<?> all(Bell<?>... bells) {
    return all(Arrays.asList(bells));
  }

  /**
   * Check if all {@code Bell}s ring successfully.
   *
   * @param bells a {@code Collection} of {@code Bell}s to check.
   * @return A {@code Bell} that will ring successfully if all of {@code bells}
   * ring successfully, or will fail otherwise.
   */
  public static Bell<?> all(final Collection<Bell<?>> bells) {
    final int len = bells.size() - Collections.frequency(bells, null);
    if (len == 0)
      return Bell.rungBell();
    if (len == 1) for (Bell b : bells)
      if (b != null) return b;
    return new Bell() {
      int succeeded = 0;
      {
        final Bell bell = this;
        for (final Bell b : bells) if (b != null) b.new Promise() {
          public void done() {
            if (++succeeded == len) bell.ring();
          } public void fail(Throwable t) {
            bell.ring(t);
          }
        };
      }
    };
  }

  /**
   * Wait for all the given {@code Bell}s to ring.
   *
   * @param bells an array of {@code Bell}s to check.
   * @return A {@code Bell} that will ring successfully if all of {@code bells}
   * ring successfully, or will fail otherwise.
   */
  public static Bell<?> wait(Bell<?>... bells) {
    return wait(Arrays.asList(bells));
  }

  /**
   * Check if all {@code Bell}s ring successfully.
   *
   * @param bells a {@code Collection} of {@code Bell}s to check.
   * @return A {@code Bell} that will ring successfully if all of {@code bells}
   * ring successfully, or will fail otherwise.
   */
  public static Bell<?> wait(Collection<Bell<?>> bells) {
    final int len = bells.size() - Collections.frequency(bells, null);
    if (len == 0)
      return Bell.rungBell();
    if (len == 1) for (Bell b : bells)
      if (b != null) return b;
    Bell bell = new Bell() {
      int finished = 0;
      public void always() {
        if (++finished >= len) ring();
      }
    };
    for (Bell b : bells)
      if (b != null) b.promise(bell);
    return bell;
  }

  /** Return a {@code Bell} rung with {@code value}. */
  public static <V> Bell<V> wrap(V value) {
    return new Bell<V>(value);
  }

  /** Return a {@code Bell} rung with {@code error}. */
  public static <V> Bell<V> wrap(Throwable error) {
    return new Bell<V>(error);
  }

  /** Print diagnostic information when this {@code Bell} rings. */
  public Bell<T> debugOnRing() {
    final StackTraceElement ste = new Throwable().getStackTrace()[1];
    return promise(new Bell<T>() {
      public void done(T t) {
        System.err.println(Bell.this+" created at...");
        System.err.println("  "+ste);
        System.err.println("...rang successfully with: ");
        System.err.println("  "+t);
      } public void fail(Throwable t) {
        System.err.println(Bell.this+" created at...");
        System.err.println("  "+ste);
        System.err.println("...failed with: ");
        t.printStackTrace();
      }
    });
  }

  /** Put some runnable task on the main dispatch queue. */
  public static void dispatch(Runnable runnable) {
    dispatcher.dispatch(runnable);
  }

  /** Put some delayed runnable task on the main dispatch queue. */
  public static void dispatch(Runnable runnable, double delay) {
    dispatcher.dispatch(runnable, delay);
  }

  /** A task for the dispatch loop. */
  private static abstract class Task implements Runnable {
    final void dispatch() {
      dispatcher.dispatch(this);
    } final void dispatch(double delay) {
      dispatcher.dispatch(this, delay);
    }
    public abstract void run();
  }
}

/** Dispatch a successful value to the given bells. */
final class DispatchDone<B extends Bell<? super C>,C> implements Runnable {
  final C object;
  final List<B> bells;
  DispatchDone(List<B> bells, C object) {
    this.bells = bells;
    this.object = object;
  } public void run() {
    for (B b : bells) b.ring(object);
  }
}

/** Dispatch a failure value to the given bells. */
final class DispatchFail<B extends Bell<?>> implements Runnable {
  final Throwable error;
  final List<B> bells;
  DispatchFail(List<B> bells, Throwable error) {
    this.bells = bells;
    this.error = error;
  } public void run() {
    for (B b : bells) b.ring(error);
  }
}

