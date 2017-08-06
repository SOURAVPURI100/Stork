package stork.module.irods;

import java.util.*;

import org.irods.jargon.core.exception.*;
import org.irods.jargon.core.pub.domain.*;
import org.irods.jargon.core.query.*;
import static org.irods.jargon.core.query.CollectionAndDataObjectListingEntry.ObjectType.*;

import stork.feather.*;
import stork.feather.util.*;
import stork.feather.Resource;

public class IRODSResource extends Resource<IRODSSession,IRODSResource>{
  protected IRODSResource(IRODSSession session, Path path) {
    super(session, path);
  }

  public Emitter<String> list() {
    final Emitter<String> emitter = new Emitter<String>();

    stat().promise(new Bell<Stat>() {
      public void done(Stat s) {
        for (Stat ss : s.files)
          emitter.emit(ss.name);
        emitter.ring();
      } public void fail(Throwable t) {
        emitter.ring(t);
      }
    });

    return emitter;
  }

  public Bell<Stat> stat() {
    return new ThreadBell<Stat>(session.executor) {
      public Stat run() throws Exception {
        List<Stat> fileList = new LinkedList<Stat>();
        List<CollectionAndDataObjectListingEntry> entries = session.actualCollection.
          listDataObjectsAndCollectionsUnderPathWithPermissions(path.toString());

        for (CollectionAndDataObjectListingEntry entry : entries) {
          Stat fileInfo = new Stat();
          fileList.add(fileInfo);

          fileInfo.file = entry.getObjectType() == DATA_OBJECT;
          fileInfo.dir  = entry.getObjectType() == COLLECTION;

          fileInfo.name = entry.getNodeLabelDisplayValue();
          fileInfo.size = entry.getDataSize();
          fileInfo.time = entry.getModifiedAt().getTime();
          List<UserFilePermission> permissionList = entry.getUserFilePermission();
          fileInfo.perm = permissionList.toString();
        }

        Stat rootStat = new Stat(path.toString());
        rootStat.setFiles(fileList);

        rootStat.name = path.name();
        rootStat.file = entries.isEmpty();
        rootStat.dir  = !rootStat.file;
        return rootStat;
      }
    }.startOn(initialize());
  }

  public Bell mkdir() {
    // Send mkdir.
    return null;
  }

  public Bell delete() {
    // Send delete.
    return null;
  }

  public IRODSTap tap() { return new IRODSTap(this); }

  public IRODSSink sink() { return new IRODSSink(this); }
}
