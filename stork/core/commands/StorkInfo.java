package stork.core.commands;

import stork.core.*;
import stork.ad.*;

public class StorkInfo extends StorkClient {
  private String type = "module";

  public StorkInfo() {
    super("info");

    args = new String[] { "[option...] [type]" };
    desc = new String[] {
      "This command retrieves information about the server itself, "+
      "such as transfer modules available and server statistics.",
      "Valid arguments for type: module (default), server, cred"
    };
  }

  public void parseArgs(String[] args) {
    assertArgsLength(args, 0, 1);
    if (args.length > 0) type = args[0];
  }

  public Ad fillCommand(Ad ad) {
    return ad.put("type", type);
  }

  public void handle(Ad ad) {
    System.out.println(ad);
  }
}
