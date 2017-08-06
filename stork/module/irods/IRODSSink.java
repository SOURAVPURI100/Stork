package stork.module.irods;

import java.util.concurrent.*;

import org.irods.jargon.core.pub.io.*;

import stork.feather.*;
import stork.feather.util.*;

public class IRODSSink extends Sink<IRODSResource>{
  IRODSStreams stream = null;
  IRODSFile destFile = null;
  ExecutorService executor;

  public IRODSSink(IRODSResource destination) {
    super(destination);
    executor = destination.session.executor;
  }

  public Bell start() {
    return new ThreadBell(executor) {
      public Object run() throws Exception {
        String irodsSource = destination().path.toString();
        destFile = destination().session.irodsFileFactory.instanceIRODSFile(irodsSource);
        stream = destination().session.stream;
        stream.openToWrite(destFile);
        return null;
      }
    }.startOn(destination().initialize());
  }

  public Bell drain(final Slice slice) {
    return new ThreadBell(executor) {
      public Object run() throws Exception {
        stream.streamBytesToFile(slice.asBytes(), slice.length());
        return null;
      }
    }.startOn(destination().initialize());
  }

  protected void finish(Throwable t) {
    if (stream != null) executor.execute(new Runnable() {
      public void run() { stream.close(); }
    });
  }
}
