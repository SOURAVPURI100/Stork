package stork.core.commands;

import java.io.*;
import java.util.*;

import stork.core.*;
import stork.core.server.*;
import stork.core.net.*;
import stork.feather.*;
import stork.util.*;
import stork.scheduler.*;

/** The {@code stork server} command handler. */
public class StorkServer extends Command {
  public StorkServer() {
    super("server");

    args = new String[] { "[option]..." };
    desc = new String[] {
      "The Stork server is the core of the Stork system, handling "+
      "connections from clients and scheduling transfers. This command "+
      "is used to start a Stork server.",
      "Upon startup, the Stork server loads stork.conf and begins "+
      "listening for client connections."
    };
    add('d', "daemonize", "run the server in the background, "+
      "redirecting output to a log file (if specified)");
    add('l', "log", "redirect output to a log file at PATH").new
      SimpleParser("PATH", true);
    add("state", "load/save server state at PATH").new
      SimpleParser("PATH", true);
  }

  public void execute(stork.ad.Ad config) {
    execute(config.unmarshal(Config.global));
  } public void execute(Config config) {
    Server s = new Server(config);
    URI[] listen = Config.global.listen;
    URI web_url = Config.global.web_service_url;

    if (listen == null || listen.length < 1)
      listen = new URI[] { Config.global.connect };

    // Initialize API endpoints.
    // TODO: Move this into Server class.
    for (URI u : listen) try {
      // Fix shorthand URIs.
      if (u.scheme() == null)
        u = URI.EMPTY.scheme(u.path().name());
      StorkInterface si = StorkInterface.create(s, u);
      Log.info("Listening for ", si.name(), " connections on: "+si.address());
    } catch (Exception e) {
      e.printStackTrace();
      Log.warning("Could not create interface: "+e.getMessage());
    }

    // Initialize web server for web documents.
    if (web_url != null) {
      String dir = "web";
      File root = new File(dir);
      if (!root.exists() || !root.isDirectory())
        Log.warning("Could not find "+dir+" directory.");
      else
        HTTPServer.createStaticServer(web_url, dir);
    }
  }
}
