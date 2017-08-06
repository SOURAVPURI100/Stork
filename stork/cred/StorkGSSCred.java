package stork.cred;

import java.util.*;
import java.io.*;

import org.ietf.jgss.*;
import org.globus.myproxy.*;
import org.globus.gsi.*;
import org.globus.gsi.gssapi.*;

import stork.ad.*;
import stork.feather.*;
import stork.feather.util.*;
import stork.util.*;

/** A wrapper for a GSS credential. */
public class StorkGSSCred extends StorkCred<Bell<GSSCredential>> {
  private String proxy_string;
  private int proxy_life = 3600;
  private String myproxy_user;
  private String myproxy_pass;
  private String myproxy_host;
  private int myproxy_port;
  private transient Bell<GSSCredential> credential;

  public StorkGSSCred() { super("gss"); }

  /** Lazily instantiate this credential. */
  public Bell<GSSCredential> data() {
    return (credential != null) ? credential : initialize();
  }

  /**
   * Call this after unmarshalling to instantiate credential from stored
   * information. Can be called again to refresh the credential as well.
   */
  private Bell<GSSCredential> initialize() {
    // TODO: Don't use a thread here.
    return new ThreadBell<GSSCredential>() {
      public GSSCredential run() throws Exception {
        if (proxy_life < 3600) {
          throw new Exception("Cred lifetime must be at least one hour.");
        } if (myproxy_user != null) {
          if (myproxy_port <= 0 || myproxy_port > 0xFFFF)
            myproxy_port = MyProxy.DEFAULT_PORT;
          MyProxy mp = new MyProxy(myproxy_host, myproxy_port);
          return mp.get(myproxy_user, myproxy_pass, proxy_life);
        } if (proxy_string != null) {
          byte[] buf = proxy_string.getBytes("UTF-8");
          InputStream is = new ByteArrayInputStream(buf);
          int usage = GSSCredential.INITIATE_AND_ACCEPT;
          X509Credential cred = new X509Credential(is);
          return new GlobusGSSCredentialImpl(cred, usage);
        } else {
          throw new Exception("Not enough information.");
        }
      }
    }.start();
  }

  // Read a certificate from a local file.
  public static StorkGSSCred fromFile(String cred_file) {
    return fromFile(new File(cred_file));
  } public static StorkGSSCred fromFile(File cred_file) {
    StorkGSSCred cred = new StorkGSSCred();
    cred.proxy_string = StorkUtil.readFile(cred_file);
    return cred;
  }

  protected Object[] hashables() {
    return new Object[] {
      proxy_string, myproxy_user, myproxy_pass, myproxy_host, myproxy_port
    };
  }
}
