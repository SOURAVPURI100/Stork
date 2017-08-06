package stork.feather.net;

import stork.feather.*;

/**
 * A {@code Coder<A,B>} transcodes messages of type {@code A} into messages of
 * type {@code B}. A {@code Coder<A,B>} can be joined with a {@code Coder<B,C>}
 * to produce a {@code Coder<A,C>}.
 */
public abstract class Coder<A,B> {
  /** Name used for debugging purposes. */
  private final String name;
  /** Either this, or another coder if proxying. */
  private final Coder<A,?> head;
  /** Either this, or the tail of the pipeline. */
  private final Coder<?,B> tail;
  /** The next coder in the pipeline. */
  private Coder<B,?> next;
  /** Rung when next is set. */
  private Bell<?> onJoin;
  /** Bell used to indicate pause status. */
  private Bell<?> pause;

  /** Create a {@code Coder} with no name. */
  public Coder() { this((String) null); }

  /** Create a {@code Coder} with a name (for debugging). */
  public Coder(String name) {
    this.name = name;
    head = this;
    tail = this;
    onJoin = new Bell();
    pause = Bell.rungBell();
  }

  // This is used to create joined codecs.
  Coder(Coder<A,?> head, Coder<?,B> tail) {
    this.name = null;
    this.head = head.head;
    this.tail = tail.tail;
  }

  /** Feed a message into the coder. */
  public final Bell<?> feed(final A a) {
    if (head != this) {
      return head.feed(a);
    } synchronized (this) {
      safeCode(a, null);
      return pause;
    }
  }

  /** Feed an error into the coder. */
  public final Bell<?> feed(final Throwable error) {
    if (head != this) {
      return head.feed(error);
    } synchronized (this) {
      safeCode(null, error);
      return pause;
    }
  }

  /** Use this instead of calling code(...) directly. */
  private synchronized void safeCode(A a, Throwable err) {
    if (err == null) try {
      code(a);
    } catch (Throwable t) {
      err = t;
    } if (err != null) try {
      code(err);
    } catch (Throwable t) {
      emit(t);
    }
  }

  /** Called by implementation to emit a message. */
  protected final synchronized Bell<?> emit(final B b) {
    if (next == null)
      pause(onJoin);
    return pause.new Promise() {
      public void done() { pause(next.feed(b)); }
    }.as(null);
  }

  /** Called to emit an error. */
  protected final synchronized Bell<?> emit(final Throwable error) {
    if (next == null)
      pause(onJoin);
    return pause.new Promise() {
      public void done() { pause(next.feed(error)); }
    }.as(null);
  }

  /**
   * Pause messages from this coder until {@code bell} rings. Once this is
   * called, messages will not emitted to the next coder until the bell rings.
   */
  public final void pause(Bell<?> bell) {
    if (head != this) {
      head.pause(bell);
    } else synchronized (this) {
      pause = pause.and(bell);
    }
  }

  /**
   * Handle coding a message fed into the coder. Once this has been called, the
   * handling of this message is the responsibility of the coder. If an
   * exception is thrown here, {@link #code(Throwable)} will be called.
   */
  protected abstract void code(A a) throws Throwable;

  /**
   * Code an error fed into the coder. If this throws, the thrown error is
   * propagated downward.
   */
  protected void code(Throwable error) throws Throwable {
    throw error;
  }

  /**
   * Join this {@code Coder<A,B>} with a {@code Coder<B,C>} coder to create a
   * {@code Coder<A,C>}. Output from the {@code Coder<A,B>} will be fed to the
   * {@code Coder<B,C>}. After joining, the {@code Coder}s are "used up", and
   * should not be used individually. Instead, all interaction with the {@code
   * Coder}s should take place through the returned composed {@code Coder}.
   */
  public final synchronized <C> Coder<A,C> join(Coder<B,C> coder) {
    if (next != null)
      throw new RuntimeException("Already joined");

    if (tail != this)
      tail.join(coder);
    next = coder.head;
    onJoin.ring();

    return new Coder<A,C>(this, coder) {
      protected final void code(A a) {
        throw new Error("This should never be called.");
      }
    };
  }

  /**
   * Combine this {@code Coder<A,B>} with a {@code Coder<B,A>} to produce a
   * {@code Codec<A,B>}.
   */
  public final Codec<A,B> combine(Coder<B,A> decoder) {
    return new Codec<A,B>(this, decoder);
  }

  /**
   * Join a {@code Coder<X,X>} to itself. This is equivalent to doing {@code
   * codec.join(codec)}, though without needing an intermediate variable.
   */
  public static <X> Coder<X,X> loop(Coder<X,X> coder) {
    return coder.join(coder);
  }

  /** The name of the coder, if it has one. Otherwise, the class name. */
  public synchronized String toString() {
    if (head != this)
      return head.toString();
    return (name != null) ? name : getClass().getSimpleName();
  }
}
