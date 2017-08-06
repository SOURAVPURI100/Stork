package stork.feather;

import java.util.concurrent.*;

import stork.feather.util.*;

/**
 * A handle on the state of a data transfer. This class contains methods for
 * controlling data flow and monitoring data throughput. This is the base class
 * for {@code ProxyTransfer} as well as any custom {@code Transfer} controller.
 * <p/>
 * Control operations are performed on a {@code Transfer} through {@code
 * public} {@code Bell} members. This allows implementors to make assumptions
 * about what states certain control methods may be called from.
 *
 * @param <S> the source {@code Resource} type.
 * @param <D> the destination {@code Resource} type.
 */
public abstract class Transfer<S extends Resource, D extends Resource> {
  public final S source;
  public final D destination;

  /** Periodically updated information about the ongoing transfer. */
  public final TransferInfo info = new TransferInfo();

  private Time timer;
  private Progress progress = new Progress();
  private Throughput throughput = new Throughput();

  private boolean startCalled = false;
  private final Bell onStart = new Bell() {
    public void done() {
      if (!Transfer.this.isDone())
        timer = new Time();
    } public void fail(Throwable t) {
      onStop.ring(t);
    }
  };
  private final Bell onStop = new Bell() {
    public void done() {
      if (timer != null) timer.stop();
      source.onTransferComplete(Transfer.this);
      destination.onTransferComplete(Transfer.this);
    } public void fail(Throwable t) {
      if (timer != null) timer.stop();
    } public void always() {
      onStart.cancel();
    }
  };

  /**
   * Create a {@code Transfer} from {@code source} to {@code destination}.
   *
   * @param source the source {@code Resource}.
   * @param destination the destination {@code Resource}.
   */
  public Transfer(S source, D destination) {
    this.source = source;
    this.destination = destination;
  }


  /** Get the source {@code Resource}. */
  public final S source() { return source; }

  /** Get the destination {@code Resource}. */
  public final D destination() { return destination; }

  /**
   * Start the transfer.
   *
   * @return This {@code Transfer}.
   */
  public final synchronized Transfer<S,D> start() {
    onStart.ring();
    return this;
  }

  /**
   * Start this transfer when {@code bell} rings.
   *
   * @param bell a {@code Bell} whose ringing indicates the transfer should
   * start. If {@code Bell} fails, the transfer fails with the same {@code
   * Throwable}.
   * @return This {@code Transfer}.
   */
  public final Transfer<S,D> startOn(Bell bell) {
    bell.promise(onStart);
    return this;
  }

  /**
   * Stop the transfer.
   *
   * @return This {@code Transfer}.
   */
  public final Transfer<S,D> stop() {
    onStop.ring();
    return this;
  }

  /**
   * Fail the transfer with the given reason. Subclasses should call {@code
   * super.stop()} when the transfer has completed.
   *
   * @param reason a {@code Throwable} indicating the reason the transfer
   * failed.
   * @return This {@code Transfer}.
   */
  public final Transfer<S,D> stop(Throwable reason) {
    onStop.ring(reason);
    return this;
  }

  /**
   * Cancel the transfer. This is equivalent to failing the transfer with a
   * {@code CancellationException}.
   *
   * @return This {@code Transfer}.
   */
  public final Transfer<S,D> cancel() {
    return stop(new CancellationException());
  }

  /**
   * Stop this transfer when {@code bell} rings.
   *
   * @param bell a {@code Bell} whoses ringing indicates the transfer should
   * stop. If {@code Bell} fails, the transfer fails with the same {@code
   * Throwable}.
   * @return This {@code Transfer}.
   */
  public final Transfer<S,D> stopOn(Bell bell) {
    bell.new Promise() {
      public void done() { start(); }
      public void fail(Throwable t) { stop(t); }
    };
    return this;
  }

  /**
   * Pause the transfer temporarily. {@code resume()} should be called to
   * resume transfer after pausing. Implementors should assume this method will
   * only be called from a running state.
   *
   * @return This {@code Transfer}.
   */
  protected Transfer<S,D> pause() {
    return this;
  }

  /**
   * Resume the transfer after a pause. Implementors should assume this method
   * will only be called from a paused state.
   *
   * @return This {@code Transfer}.
   */
  protected Transfer<S,D> resume() { return this; }

  /** Check if the transfer is complete. */
  public final boolean isDone() { return onStop.isDone(); }

  /**
   * Used by subclasses to note progress.
   */
  protected final Transfer<S,D> addProgress(long size) {
    progress.add(size);
    throughput.update(size);
    info.update(timer, progress, throughput);
    return this;
  }

  /**
   * Check if the pipeline is capable of draining {@code Slice}s in arbitrary
   * order. The return value of this method should remain constant across
   * calls.
   *
   * @return {@code true} if transmitting slices in arbitrary order is
   * supported.
   * @throws IllegalStateException if this method is called when the pipeline
   * has not been initialized.
   */
  public boolean random() { return false; }

  /**
   * Get the number of distinct {@code Resource}s the pipeline may be in the
   * process of transferring simultaneously.
   * <p/>
   * Returning a number less than or equal to zero indicates that an arbitrary
   * number of {@code Resource}s may be transferred concurrently.
   *
   * @return The number of data {@code Resource}s this sink can receive
   * concurrently.
   * @throws IllegalStateException if this method is called when the pipeline
   * has not been initialized.
   */
  public int concurrency() { return 1; }

  /**
   * Return a {@code Bell} which rings when the {@code Transfer} starts.
   *
   * @return A {@code Bell} which rings when the {@code Transfer} starts.
   */
  public final Bell<Transfer<S,D>> onStart() {
    return onStart.as(this);
  }

  /**
   * Return a {@code Bell} which rings when the {@code Transfer} stops.
   *
   * @return A {@code Bell} which rings when the {@code Transfer} stops.
   */
  public final Bell<Transfer<S,D>> onStop() {
    return onStop.as(this);
  }
}
