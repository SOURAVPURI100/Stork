package stork.core.commands;

import stork.core.*;
import stork.ad.*;

import java.io.*;
import java.util.*;

// User management stuff, like logging in, registering, or changing password.

public class StorkUser extends StorkClient {
  private String email;

  public StorkUser() {
    super("user");

    args = new String[] { "<email>" };
    desc = new String[] {
      "Log in to a Stork server or register as a new user. "+
      "You will be prompted to enter your password.",
      "This command is not completely implemented, and will only verify "+
      "that a user ID and password is correct."
    };
    add('r', "register", "register as a new user");
    add('t', "time", "length of time to remain logged in (NOT IMPLEMENTED)");
  }

  public void parseArgs(String[] args) {
    assertArgsLength(args, 1);
    email = args[0].trim().toLowerCase();
  }

  public Ad fillCommand(Ad ad) {
    Console c = System.console();

    // Make sure we're on a console.
    if (c == null) throw new RuntimeException(
      "command must be executed on an interactive console");

    // Read the password from the command line.
    // TODO: Haha, look at this security theater. Hash instead of
    // making string.
    char[] b = c.readPassword("Password: ");
    String password = new String(b);
    Arrays.fill(b, '\000');

    if (env.getBoolean("register"))
      ad.put("action", "register");
    ad.put("email", email);
    return ad.put("password", password);
  }
      
  public void handle(Ad ad) {
    if (!ad.has("error"))
      System.out.println("Logged in as: "+ad.get("email"));
  }
}
