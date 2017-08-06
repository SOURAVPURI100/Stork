package stork.scheduler;

import stork.ad.*;
import stork.util.*;
import static stork.scheduler.JobStatus.*;

import java.util.*;

/** A class for performing queries on collections of jobs. */
public class JobSearcher {
  private Collection<? extends Job> jobs;

  public JobSearcher(Collection<? extends Job> jobs) {
    this.jobs = jobs;
  }

  // Search jobs using an optional filter ad. The filter may contain the
  // following fields:
  //   range  - a range of job ids to select
  //   status - the name of a job status filter
  // The results are returned as a list.
  public List<Job> query(Ad ad) {
    List<Job> list = new LinkedList<Job>();

    // Filter fields.
    String user_id = null;
    Range range = null;
    EnumSet<JobStatus> status = JobStatus.pending.filter();

    // Parse fields from input ad.
    if (ad != null) {
      user_id = ad.get("user_id");
      if (ad.has("range"))
        range = Range.parseRange(ad.get("range"));
      if (ad.has("status"))
        status = JobStatus.byName(ad.get("status")).filter();
      else if (range != null)
        status = JobStatus.all.filter();
      if (range == null)
        range = new Range(1, jobs.size());
    }

    // Perform a simple but not very efficient O(n) selection.
    for (Job j : jobs) {
      if (status.contains(j.status()) && range.contains(j.jobId()))
        list.add(j);
    } return list;
  }
}
