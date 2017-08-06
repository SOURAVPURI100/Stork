package stork.feather.util;

import java.io.*;

import io.netty.buffer.*;

import stork.feather.*;

/**
 * A destination {@code Resource} which prints a hexadecimal representation of
 * incoming data to an {@link OutputStream} or, by default, to {@code
 * System.out}. This is used for testing Feather {@code Tap}s.
 */
public class HexDumpResource extends AnonymousResource {
  final PrintStream out;
  static final Throughput throughput = new Throughput();

  public HexDumpResource() { this(Path.ROOT); }

  public HexDumpResource(Path path) {
    super(path);
    out = System.out;
  }

  public HexDumpResource select(Path path) {
    return new HexDumpResource(path);
  }

  public Bell<HexDumpResource> mkdir() {
    out.println("Directory created at: "+path);
    return Bell.wrap(this);
  }

  public Bell<HexDumpResource> delete() {
    out.println("Directory removed at: "+path);
    return Bell.wrap(this);
  }

  public Sink<HexDumpResource> sink() {
    return new HexDumpSink(this);
  }
}

class HexDumpSink extends Sink<HexDumpResource> {
  private long total = 0;

  /** Create a {@code HexDumpSink} that prints to {@code System.out}. */
  public HexDumpSink(HexDumpResource r) { super(r); }

  public Bell start() {
    destination().out.println("Starting dump for: "+destination().path);
    return null;
  }

  public Bell drain(Slice slice) {
    destination().out.println(slice);
    //destination().out.println(slice.length());
    total += slice.length();
    HexDumpResource.throughput.update(slice.length());
    slice.asByteBuf().release();
    return null;
  }

  public void finish(Throwable t) {
    destination().out.println("End of dump: "+destination().path);
    destination().out.println("Total bytes: "+total);
    destination().out.println("Throughput:  "+HexDumpResource.throughput);
  }
}
