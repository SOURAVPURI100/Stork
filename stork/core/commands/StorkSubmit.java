package stork.core.commands;

import stork.core.*;
import stork.ad.*;
import stork.util.*;

import java.io.*;

public class StorkSubmit extends StorkClient {
  private int submitted = 0, accepted = 0;
  private Ad[] jobs = null;

  public StorkSubmit() {
    super("submit");

    args = new String[] {
      "[option...]",
      "[option...] <job_file>",
      "[option...] <src_url> <dest_url>"
    };
    desc = new String[] {
      "This command is used to submit jobs to a Stork server. ",

      "If called with no arguments, prompts the user and reads job "+
      "ads from standard input.",

      "If called with one argument, assume it's a path to a file "+
      "containing one or more job ads, which it opens and reads.",

      "If called with two arguments, assumes they are a source "+
      "and destination URL, which it parses and generates a job "+
      "ad for.",

      "After each job is submitted, submit outputs the job "+
      "ID, assuming it was submitted successfully.",

      "(Note about x509 proxies: submit will check if "+
      "\"x509_file\" is included in the submit ad, and, if so, "+
      "read the proxy file, and include its contents in the job ad "+
      "as \"x509_proxy\". This may be removed in the future.)"
    };
    add('b', "brief", "print only submitted job IDs");
  }

  private boolean parsedArgs = false;
  private boolean echo = true;

  public void parseArgs(String[] args) {
    assertArgsLength(args, 0, 2);

    // Determine if we're going to read from stream or generator our
    // own ad.
    switch (args.length) {
      case 2:  // src_url and dest_url
        jobs = new Ad[] { new Ad() };
        jobs[0].put("src",  new Ad("uri", args[0]));
        jobs[0].put("dest", new Ad("uri", args[1]));
        break;
      case 1:  // From file
        jobs = Ad.parse(new File(args[0]), true).getAds();
        break;
      case 0:  // From stdin
        if (System.console() != null) {
          echo = false;
          System.out.print("Begin typing submit ads (ctrl+D to end):\n\n");
          jobs = Ad.parse(System.console().reader(), true).getAds();
        } else {
          jobs = Ad.parse(System.in, true).getAds();
        }
    }

    if (jobs.length < 1)
      throw new RuntimeException("No job ads could be found.");
  }

  public Ad fillCommand(Ad ad) {
    Ad job = jobs[accepted];
    ad.addAll(job);

    // Replace x509_proxy in job ad.
    // TODO: A better way of doing this would be nice...
    String proxy = job.get("x509_file");
    job.remove("x509_file");

    if (proxy != null) try {
      proxy = StorkUtil.readFile(proxy);

      if (proxy.length() > 0)
        ad.put("x509_proxy", proxy);
    } catch (Exception e) {
      throw new RuntimeException("Couldn't open x509_file...", e);
    }

    // Print the command sent if we're echoing.
    if (echo)
      System.out.print(ad+"\n\n");
    return ad;
  }

  public boolean hasMoreCommands() {
    return submitted < jobs.length;
  }

  // Print status information for every submitted job.
  public void handle(Ad ad) {
    boolean error = ad.has("error");

    submitted++;
    if (!error) accepted++;

    // Don't print anything if we're being quiet.
    if (env.getBoolean("quiet")) return;

    boolean condor = env.getBoolean("condor_mode");
    boolean brief  = env.getBoolean("brief");

    // Print something if there was an error.
    if (error) {
      if (!condor) {
        System.out.print("Job submission failed! Reason: ");
        System.out.println(ad.get("error", "(unspecified)"));
      } return;
    }

    // If we're in Condor compatibility mode, print only the line Condor
    // expects and that's it.
    if (condor) {
      System.out.println("Request assigned id: "+ad.get("job_id"));
    } else if (brief) {
      System.out.println(ad.get("job_id"));
    } else if (!error) {
      // Check if the job was successfully submitted.
      System.out.println(ad);
      System.out.println("Job accepted and assigned ID: "+ad.get("job_id"));
    }
  }

  // Print some stuff at the end.
  public void complete() {
    if (raw || env.getBoolean("quiet")) return;

    if (accepted == 0) {
      throw new RuntimeException(
        "0 of "+submitted+" jobs successfully submitted");
    } else {
      System.out.println("Success: "+
        accepted+" of "+submitted+" jobs successfully submitted");
    }
  }
}
