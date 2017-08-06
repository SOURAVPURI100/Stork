package stork.feather;

import stork.feather.util.*;

/**
 * A {@code Sink} is a destination for {@link Slice}s emitted by {@link Tap}s.
 * It is the {@code Sink}'s responsibility to "drain" {@code Slice}s to the
 * associated physical resource (or other data consumer). {@code Slice}s should
 * be drained as soon as possible to, and be retained only if necessary.
 *
 * @see Tap
 * @see Slice
 *
 * @param <D> The destination {@code Resource} type.
 */
public abstract class Sink<D extends Resource> extends Pipe {
  private D destination;

  /**
   * Create a {@code Sink} associated with {@code destination}.
   *
   * @param destination the {@code Resource} this {@code Sink} receives data
   * for.
   * @throws NullPointerException if {@code destination} is {@code null}.
   */
  public Sink(D destination) {
    if (destination == null)
      throw new NullPointerException("destination");
    this.destination = destination;
  }

  public final D destination() { return destination; }

  public final Sink<D> sink() { return this; }

  public final Pipe.Orientation orientation() {
    return Pipe.Orientation.SINK;
  }

  protected Bell start() throws Exception {
    return Bell.rungBell();
  }

  /**
   * Drain a {@code Slice} to the endpoint storage system. This method returns
   * as soon as possible, with the actual I/O operation taking place
   * asynchronously.
   * <p/>
   * If the {@code Slice} cannot be drained immeditately due to congestion,
   * {@code pause()} should be called, and {@code resume()} should be called
   * when the channel is free to transmit data again.
   *
   * @param slice a {@code Slice} being drained through the pipeline.
   * @throws IllegalStateException if this method is called when the pipeline
   * has not been initialized.
   */
  protected abstract Bell drain(Slice slice) throws Exception;

  protected abstract void finish(Throwable t);
}
