package stork.core.commands;

import stork.core.*;
import stork.ad.*;
import stork.util.*;

public class StorkRm extends StorkClient {
  Range range = new Range();

  public StorkRm() {
    super("rm");

    args = new String[] { "[option...] [job_id...]" };
    desc = new String[] {
      "This command can be used to cancel pending or running jobs on "+
      "a Stork server.", "The job id, of which there may be more than "+
      "one, may be either an integer or a range of the form: "+
      "m[-n][,range] (e.g. 1-4,7,10-13)"
    };
  }

  public void parseArgs(String[] args) {
    assertArgsLength(args, 1, -1);

    for (String s : args)
      range.swallow(s);
  }

  public Ad fillCommand(Ad ad) {
    return ad.put("range", range.toString());
  }

  public void handle(Ad ad) {
    String s;
    if ((s = ad.get("removed")) != null)
      System.out.println("Removed job(s): "+s);
    if ((s = ad.get("not_removed")) != null)
      System.out.println("The follow job(s) could not be removed: "+s);
  }
}
