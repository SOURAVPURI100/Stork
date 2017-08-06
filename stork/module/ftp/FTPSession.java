package stork.module.ftp;

import io.netty.buffer.*;

import org.ietf.jgss.*;

import stork.cred.*;
import stork.feather.*;
import stork.feather.util.*;
import stork.module.*;
import stork.scheduler.*;
import stork.util.*;

/**
 * A session with an FTP server.
 */
public class FTPSession extends Session<FTPSession, FTPResource> {
  FTPChannel channel;  // The connection to the FTP server.
  boolean mlstOptsAreSet = false;  // Whether we sent OPT MLST.

  /**
   * Establish an {@code FTPSession} with the endpoint described by {@code uri}
   * and the authentication factor {@code cred}.
   */
  public FTPSession(URI uri, Credential cred) {
    super(uri, cred);
  }

  public FTPResource select(Path path) {
    return new FTPResource(this, path);
  }

  public Bell<FTPSession> initialize() {
    return new Bell() {{
      String user = "anonymous";
      String pass = "stork@storkcloud.org";

      // Initialize connection to server.
      channel = new FTPChannel(uri);

      // If the channel is closed by the server, finalize the session.
      closeWhen(channel.onClose());

      // Pull userinfo from URI.
      if (uri.username() != null)
        user = uri.username();
      if (uri.password() != null)
        pass = uri.password();

      final String finalPass = pass;

      // Act depending on the credential type.
      if (credential == null) {
        channel.authorize(user, pass).promise(this);
      } else if (credential instanceof StorkGSSCred) {
        final Bell thisBell = this;
        channel.new Lock() {{
          StorkGSSCred cred = (StorkGSSCred) credential;
          cred.data().new AsBell<FTPChannel.Reply>() {
            public Bell<Reply> convert(GSSCredential cred) throws Exception {
              return authenticate(cred);
            }
          }.new AsBell<FTPChannel.Reply>() {
            public Bell<FTPChannel.Reply> convert(FTPChannel.Reply r) {
              return authorize(":globus-mapping:", finalPass);
            }
            public void always() { unlock(); }
          }.promise(thisBell);
        }};
      } else if (credential instanceof StorkUserinfo) {
        StorkUserinfo cred = (StorkUserinfo) credential;
        user = cred.username();
        pass = cred.password();
        channel.authorize(user, pass).promise(this);
      } else {
        // Unsupported credential. Try anonymous auth.
        channel.authorize(user, pass).promise(this);
      }
    }}.as(FTPSession.this).new Promise() {
      public void done() { channel.new Command("DCAU N"); }
    };
  }

  public void cleanup() {
    channel.close();
  }

  // These methods are used by list() in FTPResource. Different FTP servers
  // respond in different ways to the MLSC and STAT commands used by list().
  // Specifically, some servers will provide a listing along with it, while
  // others will only return stats about a single resource.  These methods
  // allow for MLSC and STAT to be tested on a per-session basis.
  private Bell<Boolean> mlscCanList, statCanList;
  synchronized Bell<Boolean> cmdCanList(final FTPListCommand cmd) {
    Bell<Boolean> canList;
    switch (cmd) {
      case MLSC: canList = mlscCanList; break;
      case STAT: canList = statCanList; break;
      default  : return new Bell<Boolean>(cmd.canList());
    } if (canList != null) {
      return canList;
    } else switch (cmd) {
      case MLSC: canList = mlscCanList = new Bell<Boolean>(); break;
      case STAT: canList = statCanList = new Bell<Boolean>();
    } return channel.new Command(cmd, "/").new As<Boolean>() {
      public Boolean convert(FTPChannel.Reply r) {
        FTPListParser parser = new FTPListParser();
        if (!r.isComplete())
          return false;
        parser.parseAll(r.message().getBytes());
        Stat[] sub = parser.root.files;
        if (sub == null || sub.length != 1)
          return true;
        return !"/".equals(sub[0].name);
      } public Boolean convert(Throwable t) {
        return false;
      }
    }.promise(canList);
  }

  public static void main(String[] args) {
    String uri = (args.length > 0) ? args[0] : "ftp://didclab-ws8/stuff/";
    final Resource src = new FTPModule().select(URI.create(uri));
    src.transferTo(new HexDumpResource()).start().onStop().debugOnRing();
  }
}
