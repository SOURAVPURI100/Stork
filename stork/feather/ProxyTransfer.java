package stork.feather;

import java.util.*;
import java.util.concurrent.*;

import stork.feather.util.*;

//import java.io.*;

/**
 * A mediator for a locally proxied data transfer.
 *
 * @param <S> the source {@code Resource} type.
 * @param <D> the destination {@code Resource} type.
 */
public class ProxyTransfer<S extends Resource<?,S>, D extends Resource<?,D>>
extends Transfer<S,D> {
  private LinkedList<Pending> queue = new LinkedList<Pending>();
  private Throwable error = null;

  // A pending transfer and a bell to ring when it starts.
  private static class Pending {
    final Bell bell;
    final Path path;
    protected Pending(Path path) {
      this(new Bell(), path);
    } protected Pending(Bell bell, Path path) {
      this.bell = bell;
      this.path = path;
    }
  }

  // Sets of ongoing transfers and listings.
  private Set<Path> transfers = new HashSet<Path>();
  private Set<Path> listings = new HashSet<Path>();

  /**
   * Create a {@code ProxyTransfer} that will transfer from {@code source} to
   * {@code destination}.
   *
   * @param source the source {@code Resource}.
   * @param destination the destination {@code Resource}.
   */
  public ProxyTransfer(S source, D destination) {
    super(source, destination);

    onStart().new Promise() {
      public void done() { transfer(Path.ROOT); }
    };
  }

  // Check if we're able to start a data transfer according to the configured
  // concurrency level.
  private synchronized boolean canStartDataTransfer() {
    int c = concurrency();
    return c <= 0 || transfers.size() < c;
  }

  // The total number of tasks pending.
  private synchronized int pendingTasks() {
    return queue.size() + transfers.size() + listings.size();
  }

  // Check if the transfer is complete. If there are no more pending tasks,
  // declare the transfer to be complete.
  private synchronized void checkIfComplete() {
    if (pendingTasks() <= 0) {
      if (error != null)
        stop(error);
      else
        stop();
    }
  }

  // Transfer a resource given its path, and return a bell that rings when the
  // transfer begins.
  private synchronized Bell transfer(final Path path) {
    if (isDone()) {
      return Bell.rungBell();
    } if (!canStartDataTransfer()) {
      return enqueueTransfer(path, false);
    } try {
      transferStarted(path);
      return transfer0(path).new Promise() {
        public void fail(Throwable t) {
          error = t;
          transferEnded(path);
        }
      };
    } catch (Exception e) {
      error = e;
      transferEnded(path);
      return new Bell(e);
    }
  } private synchronized Bell transfer0(final Path path) {
    if (isDone())
      return Bell.rungBell();

    final S src  = source.select(path);
    final D dest = destination.select(path);

    // Stat the source to see what it is.
    return src.stat().new AsBell<Object>() {
      public Bell<Object> convert(Stat stat) {
        Bell b = Bell.rungBell();
        if (stat.dir)
          b = b.and(dest.mkdir()).and(transferList(path));
        if (stat.file)
          b = b.and(transferData(path));
        else
          transferEnded(path);
        return b;
      }
    };
  }

  // If we are not yet able to start a transfer, put it in the transfer queue.
  private synchronized Bell enqueueTransfer(Path path, boolean first) {
    Pending pending = new Pending(path);
    if (first)
      queue.addFirst(pending);
    else
      queue.add(pending);
    return pending.bell;
  }

  // Remove resource paths from the transfer queue and begin transferring them.
  private synchronized void popTransfers() {
    while (canStartDataTransfer()) {
      Pending pending = queue.poll();
      if (pending == null)
        return;
      transfer(pending.path).promise(pending.bell);
    }
  }

  // Transfer a resource once we know it's a data resource.
  private synchronized Bell transferData(final Path path) {
    return source.select(path).tap().attach(new Pipe() {
      protected Bell start() throws Exception {
        return super.start();
      } protected Bell drain(Slice slice) throws Exception {
        addProgress(slice.length());
        return super.drain(slice);
      } protected void finish(Throwable t) {
        super.finish(t);
        if (t != null)
          stop(t);
        transferEnded(path);
      }
    }).attach(destination.select(path).sink()).tap().start();
  }

  // Transfer directory listing.
  private synchronized Bell transferList(final Path path) {
    Emitter<String> emitter = source.select(path).list();
    final Bell bell = new Bell();
    listingStarted(path);
    emitter.new ForEach() {
      public void each(String name) {
        enqueueTransfer(path.appendLiteral(name), true);
      } public void always() {
        listingEnded(path);
      }
    };
    return Bell.rungBell();
  }

  // Called whenever a data transfer starts or completes.
  private synchronized void transferStarted(Path path) {
    transfers.add(path);
  } private synchronized void transferEnded(Path path) {
    transfers.remove(path);
    popTransfers();
    checkIfComplete();
  }

  // Called whenever a listing starts or completes.
  private synchronized void listingStarted(Path path) {
    listings.add(path);
  } private synchronized void listingEnded(Path path) {
    listings.remove(path);
    popTransfers();
    checkIfComplete();
  }
}
