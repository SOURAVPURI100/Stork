package stork.scheduler;

import java.util.*;

import stork.core.*;

/** A simple first in first out scheduler. */
public class FIFOScheduler extends Scheduler {
  private List<Job> queue = new LinkedList<Job>();
  private int running;
  private Config config = Config.global;

  protected synchronized void schedule(Job job) {
    if (config.max_jobs == 0 || running < config.max_jobs)
      runJob(job);
    else
      queue.add(job);
  }

  /** Starts the job and registers callbacks. */
  private synchronized void runJob(final Job job) {
    running++;
    job.start().new Promise() {
      protected void always() { jobTerminated(); }
    };
  }

  /** Called when a job has completed or failed to start. */
  private synchronized void jobTerminated() {
    running--;
    if (!queue.isEmpty())
      runJob(queue.remove(0));
  }
}
