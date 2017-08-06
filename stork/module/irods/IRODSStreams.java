package stork.module.irods;

import java.util.*;
import java.io.*;

import org.irods.jargon.core.connection.*;
import org.irods.jargon.core.connection.IRODSSession;
import org.irods.jargon.core.exception.*;
import org.irods.jargon.core.pub.*;
import org.irods.jargon.core.pub.io.*;
import org.irods.jargon.core.utils.*;

public class IRODSStreams extends IRODSGenericAO {
  private static final int BUFFFERSIZE = 4 * 1024;
  private final byte[] buf;
  final IRODSSession irodsSession;
  final IRODSAccount irodsAccount;

  private int bufreadlen = -1;
  private InputStream in = null;
  private OutputStream out = null;
  private DataObjectAO dataObjectAO = null;

  public IRODSStreams(IRODSSession irodsSession, IRODSAccount irodsAccount)
    throws Exception {
      super(irodsSession, irodsAccount);
      this.irodsSession = irodsSession;
      this.irodsAccount = irodsAccount;
      buf = new byte[BUFFFERSIZE];
    }

  public void openToRead(final IRODSFile irodsFile) throws Exception{
    if (irodsFile == null)
      throw new IllegalArgumentException("null irodsTargetFile");
    if (!irodsFile.exists() || !irodsFile.isFile())
      throw new Exception(irodsFile.getName()+" does not exist or is not a file");
    in = getIRODSFileFactory().instanceIRODSFileInputStream(irodsFile);
  }

  public void openToWrite(final IRODSFile irodsFile) throws Exception{
    if (irodsFile == null)
      throw new JargonException("targetIrodsFile is null");
    if (irodsFile.getResource().isEmpty())
      irodsFile.setResource(MiscIRODSUtils
          .getDefaultIRODSResourceFromAccountIfFileInZone(
            irodsFile.getAbsolutePath(),getIRODSAccount()));
    if (dataObjectAO == null)
      dataObjectAO = getIRODSAccessObjectFactory().getDataObjectAO(
          getIRODSAccount());
    out = getIRODSFileFactory().instanceIRODSFileOutputStream(irodsFile);
  }

  public byte[] streamFileToBytes() throws Exception{
    bufreadlen = in.read(buf);
    if (-1 == bufreadlen) return null;
    return Arrays.copyOf(buf, bufreadlen);
  }

  public void streamBytesToFile(byte[] bytesToStream, int length) throws Exception {
    out.write(bytesToStream, 0, length);
    out.flush();    
  }

  public void close() {
    if (null != dataObjectAO) dataObjectAO = null;
    if (null != in)  try { in.close();  } catch (Exception e) { }
    if (null != out) try { out.close(); } catch (Exception e) { }
    in = null;
    out = null;
  }
}
