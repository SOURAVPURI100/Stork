package stork.test;

import stork.feather.*;
import stork.feather.util.*;
import stork.module.ftp.*;
import stork.module.sftp.*;

/**
 * A simple test to benchmark Feather capabilities.
 */
public class BenchmarkFeather {
  public static void main(String[] args) {
    benchmarkTransfers();
    //benchmarkStreams();
    //benchmarkThroughput();
    //benchmarkOverhead();
  }

  public static void benchmarkTransfers() {
    for (int i = 1; i <= 10; i++) {
      startFTPTransfer().onStop().sync();
      System.out.println(i);
    }
  }

  public static Transfer startFTPTransfer() {
    FTPModule ftp = new FTPModule();
    SFTPModule sftp = new SFTPModule();
    Resource src = ftp.select("ftp://didclab-ws2:2121/test/100M/0");
    Resource dest = ftp.select("ftp://didclab-ws2:2121/test/100M/0");
    final Transfer t = src.transferTo(dest).start();
    new Thread() {
      public void run() {
        while (true) try {
          Thread.sleep(1000);
          if (t.onStop().isDone())
            return;
          System.out.println(t.info.avg);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }.start();
    return t;
  }

  public static Transfer startLocalTransfer() {
    Resource src = new LocalSession("/dev/zero").root();
    Resource dest = new LocalSession("/dev/null").root();
    return src.transferTo(dest).start();
  }

  public static void benchmarkStreams() {
  }

  public static void benchmarkThroughput() {
  }

  public static void benchmarkOverhead() {
  }
}

class DummyResource extends Resource {
  DummyResource() {
    super(null);
  }

  public Bell<Stat> stat() {
    Stat stat = new Stat("dummy");
    stat.file = true;
    stat.size = -1;
    return Bell.wrap(stat);
  }

  public Tap tap() {
    return new DummyTap(this);
  }

  public Sink sink() {
    return new DummySink(this);
  }
}

class DummyTap extends Tap {
  public DummyTap(Resource r) { super(r); }

  public Bell start(Bell bell) {
    return null;
  }

  public Bell drain(Slice slice) {
    return null;
  }

  public void finish(Throwable t) { }
}

class DummySink extends Sink {
  public DummySink(Resource r) { super(r); }

  public Bell start() {
    return null;
  }

  public Bell drain(Slice slice) {
    return null;
  }

  public void finish(Throwable t) { }
}
