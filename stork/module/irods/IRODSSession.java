package stork.module.irods;

import java.util.concurrent.*;

import org.irods.jargon.core.connection.*;
import org.irods.jargon.core.exception.*;
import org.irods.jargon.core.pub.*;
import org.irods.jargon.core.pub.io.*;

import stork.feather.*;
import stork.feather.util.*;

public class IRODSSession extends Session<IRODSSession,IRODSResource> {
  IRODSStreams stream;
  IRODSAccount irodsAccount;
  org.irods.jargon.core.connection.IRODSSession irodsSession;
  IRODSFileSystem irodsFileSystem;
  IRODSFileFactory irodsFileFactory;
  CollectionAndDataObjectListAndSearchAO actualCollection;

  // Per-session thread pool to work around issues with threads in Jargon.
  ExecutorService executor = Executors.newSingleThreadExecutor();

  public IRODSSession(URI uri, Credential credential) {
    super(uri, credential);
  }

  protected Bell<IRODSSession> initialize() {
    final String host = uri.host();
    final int port = uri.port();
    final String user = uri.user();
    final String pass = uri.password();
    final String zone = uri.path().explode()[0];

    return new ThreadBell<IRODSSession>() {
      public IRODSSession run() throws Exception {
        irodsAccount = new IRODSAccount(host, port, user, pass, null, zone, "");
        irodsFileSystem = IRODSFileSystem.instance();
        irodsSession = irodsFileSystem.getIrodsSession();
        irodsFileFactory = irodsFileSystem.getIRODSFileFactory(irodsAccount);

        stream = new IRODSStreams(irodsSession, irodsAccount);
        actualCollection = irodsFileSystem.getIRODSAccessObjectFactory()
          .getCollectionAndDataObjectListAndSearchAO(irodsAccount);
        return IRODSSession.this;
      }
    }.start();
  }

  protected void cleanup() {
    executor.shutdown();
  }

  public IRODSResource select(Path path) {
    return new IRODSResource(this, path);
  }
}
