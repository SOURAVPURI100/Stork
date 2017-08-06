package stork.module.irods;

import java.util.concurrent.*;

import org.irods.jargon.core.pub.io.*;

import stork.feather.*;
import stork.feather.util.*;

public class IRODSTap extends Tap <IRODSResource>{
  ExecutorService executor;

  public IRODSTap(IRODSResource source){
    super(source);
    executor = source.session.executor;
  }

  protected Bell start(final Bell start) {
    return new ThreadBell(executor) {
      public Object run() throws Exception {
        String irodsSource = source().path.toString();
        IRODSFile sourceFile = source().session.irodsFileFactory.instanceIRODSFile(irodsSource);
        final IRODSStreams stream = source().session.stream;

        stream.openToRead(sourceFile);
        start.sync();

        byte[] buf = stream.streamFileToBytes();

        while (buf != null) {
          Slice slice = new Slice(buf);
          drain(slice).debugOnRing().sync();
          buf = stream.streamFileToBytes();
        }

        finish();
        return null;
      }
    }.startOn(source().initialize());
  }
}
