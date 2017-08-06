package stork.feather.util;

import java.util.concurrent.*;
import java.util.*;

import stork.feather.*;

/**
 * A {@code Bell} which starts a {@code Thread} to generate its value. This is
 * intended to ease the integration of blocking libraries with Feather, and
 * should not otherwise be used.
 *
 * @param <T> The resolution type of this {@code ThreadBell}.
 */
public abstract class ThreadBell<T> extends Bell<T> {
  private Executor executor;
  public String string;

  private Runnable runnable = new Runnable() {
    public void run() {
      try {
        ring(ThreadBell.this.run());
      } catch (Exception e) {
        e.printStackTrace();
        ring(e);
      }
    }
  };

  /**
   * Create a {@code ThreadBell} which will create new {@code Thread}s to
   * execute {@link #run()}.
   */
  public ThreadBell() { }

  /**
   * Create a {@code ThreadBell} which will use the given {@code Executor}
   */
  public ThreadBell(Executor executor) {
    this.executor = executor;
  }

  /** Implement this to generate a value on a {@code Thread}. */
  public abstract T run() throws Exception;

  /**
   * Execute this {@code ThreadBell}'s {@code run()} method on another {@code
   * Thread}. This has no effect if the {@code ThreadBell} has already been
   * started.
   *
   * @return This {@code ThreadBell}.
   */
  public synchronized ThreadBell<T> start() {
    if (runnable != null) {
      if (executor != null) try {
        executor.execute(runnable);
      } catch (Exception e) {
        e.printStackTrace();
        ring(e);
      } else {
        new Thread(runnable).start();
      }
      executor = null;
      runnable = null;
    } return this;
  }

  /**
   * Call {@link #start()} depending on the resolution of {@code bell}. If
   * {@code bell} fails, the {@code Throwable} it fails with will be used to
   * fail this {@code ThreadBell}.
   *
   * @param bell the {@code bell} whose resolution will start this {@code
   * ThreadBell}.
   * @return This {@code ThreadBell}.
   */
  public synchronized ThreadBell<T> startOn(Bell bell) {
    bell.promise(starterBell());
    return this;
  }

  /**
   * Return a {@code Bell} that, when rung successfully, will start this {@code
   * ThreadBell}. If the returned {@code Bell} is failed, the {@code Throwable}
   * it fails with will be used to fail this {@code ThreadBell}.
   */
  public Bell starterBell() {
    return new Bell() {
      public void done() {
        start();
      } public void fail(Throwable t) {
        ThreadBell.this.ring(t);
      }
    };
  }
}
