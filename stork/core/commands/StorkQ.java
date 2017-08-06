package stork.core.commands;

import stork.core.*;
import stork.ad.*;
import stork.util.*;
import stork.feather.util.*;

public class StorkQ extends StorkClient {
  boolean count_only = false;
  boolean raw = false;
  int watch = -1;
  Range range = new Range();
  String status = null;

  public StorkQ() {
    super("q");

    prog = "q";
    args = new String[] { "[option...] [status] [job_id...]" };
    desc = new String[] {
      "This command can be used to query a Stork server for information "+
      "about jobs in queue.", "Specifying status allows filtering "+
      "of results based on job status, and may be any one of the " +
      "following values: pending (default), all, done, scheduled, "+
      "processing, removed, failed, or complete.", "The job id, of "+
      "which there may be more than one, may be either an integer or "+
      "a range of the form: m[-n][,range] (e.g. 1-4,7,10-13)"
    };
    add('c', "count", "print only the number of results");
    add('n', "limit", "retrieve up to N results")
      .new SimpleParser("N", true);
    add('r', "reverse", "reverse printing order (oldest first)");
    add('w', "watch", "fetch queue every T seconds (default 2)")
      .new SimpleParser("T", true);
    add("daglog", "output results to FILE in DAGMan log format")
      .new SimpleParser("FILE", true);
  }

  public void parseArgs(String[] args) {
    for (String s : args) try {
      range.swallow(s);
      if (status == null) status = "all";
    } catch (Exception e) {
      if (s == args[0])
        status = s;
      else
        throw new RuntimeException("invalid argument: "+s);
    }
  }

  public Ad fillCommand(Ad ad) {
    if (!range.isEmpty())
      ad.put("range", range.toString());
    if (status == null)
      status = "pending";
    ad.put("status", status);

    // Check command line options.
    if (env.has("watch"))
      watch = env.getInt("watch", 2);
    if (env.getBoolean("count"))
      ad.put("count", count_only = true);
    if (env.getBoolean("reverse"))
      ad.put("reverse", true);
    return ad;
  }

  public boolean hasMoreCommands() {
    // Sleep if we're watching.
    while (watch > 0) try {
      System.out.println("\nPress ctrl-C to stop querying.");
      Thread.sleep(watch*1000);
      break;
    } catch (Exception e) {
      // Sleep again...
    } return watch > 0;
  }

  // Print a job ad in a nice and pretty format.
  private void printTableHeader() {
    System.out.printf("%3s  %-12s  %8s  %8s  %9s  %s\n",
        "ID", "Status", "Queue", "Run", "Size", "Speed");
    System.out.printf("%3s  %-12s  %8s  %8s\n", "", "", "time", "time");
  } private String time(Ad ad) {
    if (ad == null) return "";
    Time w = ad.unmarshalAs(Time.class);
    return w.toString();
  } private String progressBar(Ad ad, int len) {
    if (ad == null) return "";
    Progress prog = ad.unmarshalAs(Progress.class);
    int j = (prog.total() > 0) ? (int)(len * prog.done() / prog.total()) : 0;
    char[] bar = new char[len];

    if (j >= len)
      j = len-1;

    for (int i = 0; i < len; i++)
      bar[i] = ' ';
    for (int i = 0; i < j; i++)
      bar[i] = '=';
    if (prog.done() > 0)
      bar[j] = (prog.done() != prog.total()) ? '>' : '=';
    return "    ["+new String(bar)+"] "+prog.toPercent();
  } private void formatJobAd(Ad ad) {
    System.out.printf("%3d  %-12s  %8s  %8s  %9s  %s\n",
        ad.getInt("job_id"), ad.get("status", "(unknown)"),
        time(ad.getAd("queue_timer")), time(ad.getAd("run_timer")),
        Throughput.prettySize(ad.getLong("progress.bytes.total")),
        Throughput.format(ad.getLong("progress.bytes.avg")));
    if (ad.getInt("progress.bytes.avg") > 0)
      System.out.println(progressBar(ad.getAd("progress.bytes"), 50));

    System.out.println("    "+ad.get("src.uri"));
    System.out.println("    "+ad.get("dest.uri"));

    if (ad.has("message"))
      System.out.println("    Message: "+ad.get("message"));

    System.out.println();
  }

  public void handle(Ad ad) {
    // Check if we just wanted the count.
    if (count_only) {
      if (ad.isMap() && ad.has("error"))  // Should we print instead?
        System.out.println(0);
      else if (ad.has("count"))
        System.out.println(ad.getInt("count"));
      else
        System.out.println(ad.size());
      return;
    }

    // If we're watching, clear the screen. TODO: Portability.
    if (watch > 0) {
      System.out.print("\033[H\033[2J");
      System.out.print("Querying every "+watch+"s...\n\n");
    }

    // Print this so the user knows what exactly was requested.
    if (status != null) {
      System.out.print("Searching for "+status+" jobs");
      if (!range.isEmpty())
        System.out.println(" with ID(s): "+range+"\n");
      else
        System.out.println("...\n");
    } else if (!range.isEmpty()) {
      System.out.println("Searching for jobs with ID(s): "+range+"\n");
    }

    // Check if there was an error.
    if (ad.isMap()) {
      if (ad.has("message"))
        throw new RuntimeException(ad.get("message"));
      throw new RuntimeException("Unexpected reply: "+ad);
    }

    // Print all the job ads. TODO: Better formatting.
    if (!raw && ad.size() > 0) {
      printTableHeader();
      for (Ad a : ad.getAds())
        formatJobAd(a);
    } else if (raw) {
      System.out.println(ad);
    }

    // Report how many ads we received.
    if (ad.size() > 0)
      System.out.println("Found "+ad.size()+" job(s).");
    else
      System.out.println("No jobs found.");
  }
}
