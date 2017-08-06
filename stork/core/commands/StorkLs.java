package stork.core.commands;

import stork.core.*;
import stork.ad.*;
import stork.util.*;

import java.util.*;
import java.text.*;

// Handler for performing remote listings.
public class StorkLs extends StorkClient {
  String uri = null;
  boolean all = false;
  boolean long_fmt = false;
  int count = 0;  // The number of subdirs we're printing.

  public StorkLs() {
    super("ls");

    args = new String[] { "[option...] <url>" };
    desc = new String[] {
      "This command can be used to list the contents of a remote "+
      "directory."
    };
    add('d', "depth", "list up to N levels").new SimpleParser("N", false);
    add('r', "recursive", "recursively list subdirectories");
    add('a', "all", "include hidden directories in listing");
    add('l', "long", "show long form listing information");
  }

  public void parseArgs(String[] args) {
    assertArgsLength(args, 1);
    try {
      uri = new java.net.URI(args[0]).normalize().toString();
    } catch (Exception e) {
      throw new RuntimeException("this command expects a URI argument");
    }
  }

  public Ad fillCommand(Ad ad) {
    // Check for options.
    ad.put("uri", uri);
    if (env.getBoolean("recursive"))
      ad.put("depth", env.getInt("depth", -1));
    all = env.getBoolean("all");
    long_fmt = env.getBoolean("long");
    return ad;
  }

  // Flatten the tree structure for listing.
  private List<Ad> flatten(List<Ad> list, Ad ad) {
    count++;
    list.add(ad);
    if (ad.has("files")) for (Ad a : ad.getAds("files"))
      if (a.has("files")) flatten(list, a);
    return list;
  }

  // Print a single directory listing ad.
  private void printListing(Ad ad) {
    if (ad.getBoolean("dir")) {
      Ad[] files = ad.getAds("files");
      if (files == null || files.length == 0)
        return;
      if (count > 1)
        System.out.println(ad.get("name")+":");
      for (Ad a : files)
        printEntry(a);
    } else {
      printEntry(ad);
    }
  }

  // Helper methods for printing long listing information.
  DateFormat df1 = new SimpleDateFormat("MMM dd HH:mm");
  DateFormat df2 = new SimpleDateFormat("MMM dd  yyyy");
  String dateString(long time) {
    if (time < 0) return "(unknown)";
    // Why is this so atrocious in Java.
    Date d = new Date(time*1000);
    Calendar c = Calendar.getInstance();
    c.setTime(d);
    int dy = c.get(Calendar.YEAR);
    int cy = Calendar.getInstance().get(Calendar.YEAR);
    DateFormat fmt = (cy == dy) ? df1 : df2;
    return fmt.format(d);
  }

  // Print a single list entry. Pretty icky.
  private void printEntry(Ad ad) {
    String name = ad.get("name");
    if (name == null)
      return;
    if (!all && name.startsWith("."))
      return;
    if (ad.getBoolean("dir"))
      name += '/';

    if (long_fmt) {
      // Get the permissing string.
      String p = ad.get("perm", "?");
      // Format the date.
      String d = dateString(ad.getLong("time"));
      // Get the size.
      String s = StorkUtil.prettySize(ad.getLong("size", 0));
      System.out.printf("%9s %6s %12s %s\n", p, s, d, name);
    } else {
      System.out.println(name);
    }
  }

  public void handle(Ad ad) {
    if (ad.has("error"))
      return;

    // Print everything from the list.
    for (Ad a : flatten(new LinkedList<Ad>(), ad))
      printListing(a);
  }
}
