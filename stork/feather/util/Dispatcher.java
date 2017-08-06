package stork.feather.util;

import java.util.*;

/**
 * A dispatch loop used internally. The dispatch loop maintains a timer thread
 * that will be destroyed when there are no dispatched tasks.
 */
public class Dispatcher {
  private String name;
  private long count;
  private Timer timer;

  /** Create an unnamed {@code Dispatcher}. */
  public Dispatcher() { this(null); }

  /** Create a {@code Dispatcher} with the given name. */
  public Dispatcher(String name) { this.name = name; }

  // Wrap a runnable for safety.
  private TimerTask wrap(final Runnable r) {
    return new TimerTask() {
      public void run() {
        try {
          r.run();
        } catch (Exception e) {
          // Ignore.
        } finally {
          dispatchFinished();
        }
      }
    };
  }

  /** Schedule {@code runnable} to be executed as soon as possible. */
  public synchronized void dispatch(final Runnable runnable) {
    dispatch(runnable, 0);
  }

  /** Schedule {@code runnable} to be executed after a delay. */
  public synchronized void dispatch(Runnable runnable, double delay) {
    if (timer == null)
      timer = new Timer(name == null ? "Dispatcher" : name);
    timer.schedule(wrap(runnable), (long)(delay*1E3));
    count++;
  }

  /** Called when a dispatch has completed. */
  private synchronized void dispatchFinished() {
    if (--count == 0) {
      timer.cancel();
      timer = null;
    }
  }
}
