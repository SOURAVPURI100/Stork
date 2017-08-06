package stork.core.commands;

import stork.core.*;
import stork.ad.*;
import stork.util.*;

import java.io.*;
import java.util.*;

// For instantiating credentials from the command line.
// TODO: Do this non-interactively as well.

public class StorkCred extends StorkClient {
  private String token = null;
  private String cmd = null;
  private String type = null;
  private int time;

  public StorkCred() {
    super("cred");

    args = new String[] { "add <type>" };
    desc = new String[] {
      "Register a new credential with the server.",
      "The following credential types are supported: "+
      "myproxy, x509proxy, userinfo"
    };
    add('t', "time", "credential validity duration");
  }

  public void parseArgs(String[] args) {
    assertArgsLength(args, 2);
    if (args.length == 1) {
      token = args[0].toLowerCase();
    } else if (args.length == 2) {
      cmd  = args[0].toLowerCase();
      type = args[1].toLowerCase();
    }
  }

  private Console console() {
    Console c = System.console();

    // Make sure we're on a console.
    if (c == null) throw new RuntimeException(
      "command must be executed on an interactive console");
    return c;
  }

  private Ad readUserinfo() {
    String user = console().readLine("Username: ");
    char[] b    = console().readPassword("Password: ");
    String pass = new String(b);

    return new Ad("type", "userinfo")
             .put("user", user)
             .put("pass", pass);
  }

  private Ad readX509() {
    String path = console().readLine("Path: ");
    String data = StorkUtil.readFile(path);

    Ad ad = new Ad("type", "gss-cert")
              .put("proxy_string", data);
    if (env.has("time"))
      ad.put("proxy_life", env.getInt("time"));
    return ad;
  }

  private Ad readMyProxy() {
    String host = console().readLine("MyProxy host: ");
    String user = console().readLine("Username: ");
    char[] b    = console().readPassword("Password: ");
    String pass = new String(b);

    Ad ad = new Ad("type", "gss-cert")
              .put("myproxy_host", host)
              .put("myproxy_user", user)
              .put("myproxy_pass", pass);
    if (env.has("time"))
      ad.put("proxy_life", env.getInt("time"));
    return ad;
  }

  public Ad fillCommand(Ad ad) {
    if (type == null)
      return ad.put("cred", token);
    if (!cmd.equals("add"))
      throw new RuntimeException("unknown command: "+cmd);
    ad.put("action", "create");
    if (type.equals("myproxy"))
      return ad.addAll(readMyProxy());
    if (type.equals("x509proxy"))
      return ad.addAll(readX509());
    if (type.equals("userinfo"))
      return ad.addAll(readUserinfo());
    throw new RuntimeException("unsupported type: "+type);
  }
      
  public void handle(Ad ad) {
    System.out.println(ad);
  }
}
