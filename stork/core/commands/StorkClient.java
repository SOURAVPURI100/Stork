package stork.core.commands;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

import stork.core.*;
import stork.ad.*;
import stork.feather.URI;
import stork.util.*;

// The base class for all client commands.

public abstract class StorkClient extends Command {
  protected Ad env = null;
  protected boolean raw = false;

  public StorkClient(String prog) {
    super(prog);
    add('R', "raw", "display raw server responses");
  }

  // Execute a command on the connected Stork server.
  public final void execute(Ad env) {
    this.env = env;
    Socket sock = connect(Config.global.connect);
    raw = env.getBoolean("raw");

    // TODO: Ugh, this is really idiotic here too, deal with it later.
    env.unmarshal(Config.global);
    env.addAll(Ad.marshal(Config.global));

    try {
      InputStream  is = sock.getInputStream();
      OutputStream os = sock.getOutputStream();
      Ad ad;

      // Some sanity checking
      if (is == null || os == null)
        throw new Exception("problem with socket");

      // Write command ad to the server.
      do {
        ad = fillCommand(new Ad().put("command", prog));

        // Write command to server.
        os.write((ad+"\n").getBytes("UTF-8"));
        os.flush();

        ad = Ad.parse(is);

        if (ad == null)
          throw new RuntimeException("incomplete response from server");
        if (raw)
          System.out.println(ad);
        else
          handle(ad);
      } while (hasMoreCommands());

      if (ad.has("error"))
        throw new RuntimeException("from server: "+ad.get("error"));
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      complete();
    }
  }

  // TODO: Support for different endpoints.
  private static Socket connect(URI u) {
    try {
      return new Socket(u.host(), u.port());
    } catch (Exception e) {
      throw new RuntimeException("couldn't connect to: "+u, e);
    }
  }

  ////////////////////////////
  // Override these things. //
  ////////////////////////////

  // Override this if the handler sends multiple command ads.
  public boolean hasMoreCommands() {
    return false;
  }

  // Return the command ad to send to the server.
  public Ad fillCommand(Ad ad) {
    return ad.addAll(env.getAd("args"));
  }

  // Handle each response from the server.
  public void handle(Ad ad) {
    System.out.println(ad);
  }

  // Anything else that needs to be done at the end of a command.
  public void complete() { }
}
