package stork.core;

import java.util.*;

import stork.ad.*;
import stork.core.commands.*;
import stork.feather.*;
import stork.feather.util.Throughput;
import stork.util.*;

/**
 * The {@code stork} command without any arguments.
 *
 * Handles parsing config files and command line arguments (or rather
 * handles delegating command line parsing, to be precise) and initing
 * and running Stork commands according to passed arguments.
 */
public class Main extends Command {
  private static String version = null;

  static {
    // Register a handler to marshal strings in ads into Feather URIs.
    new Ad.Marshaller<URI>(URI.class) {
      public String marshal(URI uri) {
        return uri.toString();
      } public URI unmarshal(String uri) {
        return URI.create(uri);
      }
    };

    // Register a handler to marshal UUIDs.
    new Ad.Marshaller<UUID>(UUID.class) {
      public String marshal(UUID uuid) {
        return uuid.toString();
      } public UUID unmarshal(String uuid) {
        return UUID.fromString(uuid);
      }
    };

    // Register a handler to unmarshal StorkCreds.
    new Ad.Marshaller<stork.cred.StorkCred>(stork.cred.StorkCred.class) {
      public stork.cred.StorkCred unmarshal(Object o) {
        Ad ad = Ad.marshal(o);
        stork.cred.StorkCred cred =
          stork.cred.StorkCred.newFromType(ad.get("type"));
        ad.unmarshal(cred);
        return cred;
      }
    };
  }

  /** Try to get the version and build time from the build tag. */
  public static String version() {
    if (version != null) {
      return version;
    } try {
      String app = "Stork", ver = "", bts = "unknown";
      Properties props = new Properties();
      props.load(Main.class.getResourceAsStream("/build_tag"));
      app = props.getProperty("appname", app);
      ver = props.getProperty("version", ver);
      bts = props.getProperty("buildtime", bts);
      return version = StorkUtil.join(app, ver, '('+bts+')');
    } catch (Exception e) {
      return "";
    }
  }

  private Main() {
    super("stork");

    // Construct the bare command line parser.
    args = new String[] { "<command> [args]", "[option...]" };

    // Add command options.
    add('V', "version", "display the version number and exit").new
      Parser<Void>() {
        public Void handle() {
          System.out.println(version());
          System.exit(0);
          return null;
        }
      };
    // FIXME: This is a little broken because it reads the default
    // config file first.
    add('C', "conf", "specify custom path to stork.conf").new
      Parser<Void>("PATH", true) {
        public Void handle(String arg) {
          Config.global.loadConfig(arg);
          return null;
        }
      };
    add('q', "quiet", "don't print anything to standard output");

    // Register subcommands.
    add("server", "start the Stork server", StorkServer.class);
    add("q", "query the Stork queue", StorkQ.class);
    add("status", "an alias for the q command", StorkQ.class);
    add("rm", "cancel or unschedule a job", StorkRm.class);
    add("ls", "retrieve a directory listing", StorkLs.class);
    add("info", "view Stork server settings", StorkInfo.class);
    add("raw", "send commands for debugging", StorkRaw.class);
    add("submit", "submit a job to the server", StorkSubmit.class);
    add("user", "log in or register", StorkUser.class);
    add("cred", "register a credential", StorkCred.class);

    foot = new String[] {
      "Stork is still undergoing testing and development. "+
      "If you encounter any bugs, please file an issue report at "+
      "<https://github.com/didclab/stork/issues>.", version()
    };
  }

  public static void main(String[] args) {
    Config.global.loadConfig(null);
    new Main().parseAndExecute(args);
  }
}
