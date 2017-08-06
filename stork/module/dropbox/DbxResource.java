package stork.module.dropbox;

import java.util.*;

import com.dropbox.core.*;

import stork.feather.*;
import stork.feather.errors.*;
import stork.feather.util.*;

public class DbxResource extends Resource<DbxSession, DbxResource> {
  DbxResource(DbxSession session, Path path) {
    super(session, path);
  }

  public synchronized Emitter<String> list() {
    final Emitter<String> emitter = new Emitter<String>();
    new ThreadBell() {
      public Object run() throws Exception {
        DbxEntry.WithChildren listing = session.client.getMetadataWithChildren(path.toString());
        for (DbxEntry child : listing.children)
          emitter.emit(child.name);
        emitter.ring();
        return null;
      }
    }.startOn(initialize());
    return emitter;
  }

  public synchronized Bell<Stat> stat() {
    return new ThreadBell<Stat>() {
      public Stat run() throws Exception {
        DbxEntry dbe = session.client.getMetadata(path.toString());

        if (dbe == null)
          throw new NotFound();

        Stat stat = entryToStat(dbe);
        stat.name = path.name();

        if (stat.dir) {
          DbxEntry.WithChildren dbd =
            session.client.getMetadataWithChildren(path.toString());
          List<Stat> sub = new LinkedList<Stat>();
          for (DbxEntry child : dbd.children)
            sub.add(entryToStat(child));
          stat.setFiles(sub);
        }

        return stat;
      }
    }.startOn(initialize());
  }

  private Stat entryToStat(DbxEntry dbe) {
    Stat stat = new Stat(dbe.name);

    if (stat.file = dbe.isFile()) {
      DbxEntry.File file = dbe.asFile();
      stat.size = file.numBytes;
      stat.time = file.lastModified.getTime()/1000;
    } else {
      stat.dir = true;
    }

    return stat;
  }

  public Tap<DbxResource> tap() {
    return new DbxTap();
  }

  public Sink<DbxResource> sink() {
    return new DbxSink();
  }

  private class DbxTap extends Tap<DbxResource> {
    protected DbxTap() { super(DbxResource.this); }

    protected Bell start(Bell bell) {
      return new ThreadBell() {
        public Object run() throws Exception {
          session.client.getFile(
            source().path.toString(), null, asOutputStream());
          finish();
          return null;
        } public void fail(Throwable t) {
          finish();
        }
      }.startOn(initialize().and(bell));
    }
  }

  private class DbxSink extends Sink<DbxResource> {
    private DbxClient.Uploader upload;

    protected DbxSink() { super(DbxResource.this); }

    protected Bell<?> start() {
      return initialize().and((Bell<Stat>)source().stat()).new As<Void>() {
        public Void convert(Stat stat) throws Exception {
          upload = session.client.startUploadFile(
            destination().path.toString(),
            DbxWriteMode.force(), stat.size);
          return null;
        } public void fail(Throwable t) {
          finish(t);
        }
      };
    }

    protected Bell drain(final Slice slice) {
      return new ThreadBell<Void>() {
        public Void run() throws Exception {
          upload.getBody().write(slice.asBytes());
          return null;
        }
      }.start();
    }

    protected void finish(Throwable t) {
      try {
        upload.finish();
      } catch (Exception e) {
        // Ignore...?
      } finally {
        upload.close();
      }
    }
  }
}
