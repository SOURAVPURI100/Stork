package stork.feather.util;

import java.util.*;
import java.util.concurrent.*;

import stork.feather.*;

/**
 * A {@code Session} capable of interacting with the local file system. This is
 * intended to serve as an example Feather implementation, but can also be used
 * for testing other implementations.
 * <p/>
 * Many of the methods in this session implementation perform long-running
 * operations concurrently using threads because this is the most
 * straighforward way to demonstrate the asynchronous nature of session
 * operations. However, this is often not the most efficient way to perform
 * operations concurrently, and ideal implementations would use an alternative
 * method.
 */
public class LocalSession extends Session<LocalSession,LocalResource> {
  final ScheduledThreadPoolExecutor executor =
    new ScheduledThreadPoolExecutor(1);
  final Path path;

  /** Create a {@code LocalSession} at the system root. */
  public LocalSession() { this(Path.ROOT); }

  /** Create a {@code LocalSession} at {@code path}. */
  public LocalSession(String path) {
    this(Path.create(path));
  }

  /** Create a {@code LocalSession} at {@code path}. */
  public LocalSession(Path path) {
    super(URI.EMPTY.scheme("file").path(path));
    this.path = path;
  }

  public LocalResource select(Path path) {
    return new LocalResource(this, path);
  }

  protected void finalize() {
    executor.shutdown();
  }

  public static void main(String[] args) {
    String sp = args.length > 0 ? args[0] : "/home/bwross/test";
    final Path path = Path.create(sp);
    final LocalResource s = new LocalSession(path).root();
    final HexDumpResource d = new HexDumpResource();

    Transfer t = s.transferTo(d);
    t.start();
    t.onStop().new Promise() {
      public void always() {
        s.session.close();
      }
    }.sync();
  }
}
