package stork.feather.util;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;

import io.netty.buffer.*;
import io.netty.util.*;

import stork.feather.*;

/**
 * A utility class for creating {@link Pipe}s, {@link Tap}s, and {@link Sink}s
 * for various purposes.
 */
public final class Pipes {
  private Pipes() { }

  /**
   * Create an anonymous {@code Tap} which emits a single {@code Slice} to an
   * attached {@code Sink}. If {@code slice} is {@code null}, the returned
   * {@code Tap} will initialize the attached {@code Sink}, but will not emit
   * any {@code Slice}s.
   *
   * @param slice the {@code Slice} emitted by the returned {@code Tap}.
   * @return An anonymous {@code Tap} which will emit {@code slice}.
   */
  public static Tap tapFromSlice(final Slice slice) {
    return Resources.fromSlice(slice).tap();
  }

  /**
   * Create a {@code Tap} which emits the given {@code Slice} for the given
   * {@code Resource} {@code root}.
   */
  public static Tap tapFromSlice(Resource root, final Slice slice) {
    return new Tap(root) {
      public Bell start(Bell bell) {
        return bell.new Promise() {
          public void done() {
            if (slice != null)
              drain(slice);
            finish();
          } public void fail(Throwable t) {
            finish(t);
          }
        };
      }
    };
  }

  /**
   * Create an anonymous {@code Tap} which emits the {@code String}
   * representation of an object encoded as UTF-8.
   *
   * @param object the object to stringify and emit.
   * @return An anonymous {@code Tap} which will emit {@code object} as a
   * {@code String} encoded using UTF-8.
   */
  public static Tap tapFromString(Object object) {
    return tapFromString(object, CharsetUtil.UTF_8);
  }

  /**
   * Create an anonymous {@code Tap} which emits the {@code String}
   * representation of an object encoded using {@code charset}.
   *
   * @param object the object to stringify and emit.
   * @param charset the {@link Charset} to use for encoding.
   * @return An anonymous {@code Tap} which will emit {@code object} as a
   * {@code String} using {@code charset}.
   */
  public static Tap tapFromString(Object object, Charset charset) {
    if (object == null)
      return Resources.fromSlice(null).tap();
    CharBuffer cb = CharBuffer.wrap(object.toString());
    ByteBufAllocator allo = UnpooledByteBufAllocator.DEFAULT;
    ByteBuf bb = ByteBufUtil.encodeString(allo, cb, charset);
    return tapFromSlice(new Slice(bb));
  }

  /**
   * A {@code Pipe} which aggregates multiple, randomly-ordered {@code Slices}
   * into a single {@code Slice} which it drains on completion.
   */
  public static Pipe aggregatorPipe() {
    return new Pipe() {
      private List<ByteBuf> list = new LinkedList<ByteBuf>();

      public Bell drain(Slice slice) {
        list.add(slice.asByteBuf());
        return null;
      }

      public void finish(Throwable t) {
        ByteBuf[] array = list.toArray(new ByteBuf[0]);
        ByteBuf buf = Unpooled.wrappedBuffer(array);
        try {
          super.drain(new Slice(buf));
        } catch (Exception e) {
          // What to do here...
        }
      }
    };
  }

  /**
   * A {@code Sink} which receives and aggregates {@code Slice}s.
   */
  public static class AggregatorSink extends Sink {
    private Bell<Slice> bell = new Bell<Slice>();
    private List<ByteBuf> list = new LinkedList<ByteBuf>();

    public AggregatorSink(Resource r) { super(r); }

    public Bell<Slice> bell() { return bell; }

    public Bell drain(Slice slice) {
      list.add(slice.asByteBuf());
      return null;
    }

    public void finish(Throwable t) {
      ByteBuf[] array = list.toArray(new ByteBuf[0]);
      ByteBuf buf = Unpooled.wrappedBuffer(array);
      bell.ring(new Slice(buf));
    }
  }

  /**
   * Get an {@code AggregatorSink} for an anonymous {@code Resource}.
   */
  public static AggregatorSink aggregatorSink() {
    return new AggregatorSink(Resources.anonymous());
  }

  /**
   * View {@code pipe} as an {@code InputStream}. This will attach to {@code
   * pipe}, but will not start it.
   */
  public static InputStream asInputStream(Pipe pipe) {
    return new PipeInputStream(pipe);
  }
}

/**
 * View a Tap as an InputStream.
 */
class PipeInputStream extends InputStream {
  /** Queue of buffers received. */
  private LinkedBlockingDeque<ByteBuffer> buffers =
    new LinkedBlockingDeque<ByteBuffer>();
  /** Ring on read. */
  private Bell readBell = new Bell();
  /** True when tap has finished. */
  private boolean done;
  /** Non-null if tap finished with error. */
  private IOException error;
  /** The current reader. */
  private Thread reader;

  /** Create a PipeInputStream from pipe. */
  public PipeInputStream(Pipe pipe) {
    pipe.attach(new Sink(Resources.anonymous()) {
      public Bell start() {
        return readBell.detach();
      } public Bell drain(Slice slice) {
        return handleDrain(slice);
      } public void finish(Throwable t) {
        handleFinish(t);
      }
    });
  }

  private synchronized void handleFinish(Throwable t) {
    if (t != null)
      error = new IOException(t);
    done = true;
    if (reader != null)
      reader.interrupt();
  }

  /**
   * Save the slice and hold until it has been read out.
   */
  private synchronized Bell handleDrain(Slice slice) {
    ByteBuffer buf = slice.asByteBuffer();
    if (buf.remaining() > 0)
      buffers.add(slice.asByteBuffer());
    return readBell.detach();
  }

  public int read() throws IOException {
    byte[] b = new byte[1];
    if (read(b, 0, 1) <= 0)
      return -1;
    return b[0];
  }

  /**
   * Read from the tap. This allows bytes to flow from the tap, filling the
   * given byte array with data until the read request has been satisfied or
   * the tap finishes.
   */
  public int read(byte[] b, int off, int len) throws IOException {
    if (isDone())
      return -1;
    synchronized (this) {
      readBell.ring();  // Allow the tap to flow.
      if (reader != null)
        throw new IllegalStateException("Already reading.");
      reader = Thread.currentThread();
    }
    int total = 0;

    // Keep taking buffers and filling b until done.
    while (!isDone() && total < len) try {
      ByteBuffer buf = buffers.take();
      int size = Math.min(len-total, buf.remaining());
      buf.get(b, off+total, size);
      total += size;
      if (buf.hasRemaining())
        buffers.addFirst(buf);
    } catch (InterruptedException e) {
      break;
    }

    synchronized (this) {
      readBell = new Bell();
      reader = null;
    }
    return total;
  }

  /**
   * Return true if there are no buffered bytes and the tap is done. If the tap
   * is done, but finished with an error, throw the error wrapped in an
   * IOException.
   */
  private synchronized boolean isDone() throws IOException {
    if (error != null)
      throw error;
    return done && buffers.isEmpty();
  }
}
