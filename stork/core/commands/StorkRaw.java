package stork.core.commands;

import stork.core.*;
import stork.ad.*;

import java.io.*;

// Handler for sending a raw command ad to the server.

public class StorkRaw extends StorkClient {
  String file;

  public StorkRaw() {
    super("raw");

    args = new String[] { "[ad_file]" };
    desc = new String[] {
      "Send a raw command ad to a server, for debugging purposes.",
      "If no input ad is specified, reads from standard input."
    };
  }

  public void parseArgs(String[] args) {
    assertArgsLength(args, 0, 1);
    if (args.length > 0)
      file = args[0];
  }

  public Ad fillCommand(Ad ad) {
    if (file != null) try {
      return Ad.parse(new File(file));
    } catch (Exception e) {
      throw new RuntimeException("couldn't read ad from file");
    } else try {
      if (System.console() != null) {
        System.out.print("Type a command ad (ctrl-C to cancel):\n\n");
        return Ad.parse(System.console().reader());
      } return Ad.parse(System.in);
    } catch (Exception e) {
      throw new RuntimeException("couldn't read ad from stream");
    }
  }
      
  public void handle(Ad ad) {
    System.out.println(ad);
  }
}
