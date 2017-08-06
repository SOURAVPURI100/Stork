package stork.scheduler;

import java.util.*;
import java.util.concurrent.*;

import stork.ad.*;
import stork.core.*;
import stork.core.server.*;
import stork.core.handlers.*;
import stork.feather.*;
import stork.feather.util.*;
import stork.util.*;

import static stork.scheduler.JobStatus.*;
import static stork.feather.util.Time.now;

/**
 * A job that should be scheduled to run. Currently this only means transfer
 * jobs. In the future we will likely have different types of jobs, such as
 * deletion jobs.
 */
public class Job {
  private JobStatus status = scheduled;
  private JobEndpointRequest src, dest;
  private int attempts = 0, max_attempts = 10;
  private String message;

  /** Byte progress of the transfer. */
  public TransferInfo bytes;
  /** File progress of the transfer. Currently unused. */
  public TransferInfo files;

  /** An ID meaningful to the user who owns the job. */
  public int job_id;

  /** The owner of the job. */
  public String owner;  // FIXME: What if the email changes?
  private transient User user;

  /** Should be set by scheduler. */
  public transient Scheduler scheduler;

  /** The {@code User} this {@code Job} belongs to. */
  public User user() {
    if (user == null)
      user = scheduler().server().findUser(owner);
    return user;
  }

  private UUID uuid;

  /** A UUID used to identify the job in the system. */
  public synchronized UUID uuid() {
    if (uuid == null)
      uuid = UUID.randomUUID();
    return uuid;
  }

  /** Times of various important events. */
  private Times times = new Times();
  private static class Times {
    Long scheduled, started, completed;
  }

  private transient Transfer transfer;

  protected Scheduler scheduler() { return scheduler; }

  private class JobEndpointRequest extends EndpointRequest {
    public Server server() { return scheduler().server(); }
    public User user() { return Job.this.user(); }
  }

  public int jobId() {
    return job_id;
  }

  public Job jobId(int id) {
    job_id = id;
    return this;
  }

  /* Get file size */
  /*public int bytes(){//TODO
    return jo
  }*/

  /** Get the status of the job. */
  public synchronized JobStatus status() { return status; }

  /**
   * Set the status of the job. This will perform a state transition, and
   * update the state of the job as necessary.
   */
  public synchronized Job status(JobStatus status) {
    if (status == null || status.isFilter)
      throw new Error("Cannot set job state to status: "+status);

    if (this.status == status)
      return this;

    // Handle leaving the current state.
    if (this.status != null) switch (this.status) {
      case processing:
        if (transfer != null)
          transfer.stop();
        transfer = null;
    }

    // Handle entering the new state.
    switch (this.status = status) {
      case scheduled:
        times.scheduled = now(); break;
      case processing:
        times.started = now(); break;
      case removed:
      case failed:
      case complete:
        if (transfer != null)
          transfer.cancel();
        times.completed = now(); break;
    } return this;
  }

  /** Set the message associated with the job. */
  public synchronized Job message(String message) {
    this.message = message;
    return this;
  }

  /** Set the status and job message at the same time, for convenience. */
  public synchronized Job status(JobStatus status, String message) {
    message(message);
    status(status);
    return this;
  }

  /** Called when the job gets removed from the queue. */
  public synchronized Job remove(String reason) {
    if (isDone())
      throw new RuntimeException("The job has already terminated.");
    return status(removed, reason);
  }

  /**
   * Completely restarts the job. This will reset any state associated with the
   * job and start it again as if it is the first time it has been seen by the
   * system. This should only ever be called as a result of a user request.
   */
  public synchronized Job restart() {
    // TODO
    return this;
  }

  /** Reschedule the job, if possible. */
  public synchronized Job reschedule() {
    if (!canBeScheduled())
      throw new RuntimeException("Job cannot be automatically rescheduled.");
    status(scheduled);
    scheduler.schedule(this);
    return this;
  }

  private boolean hasMoreAttempts() {
    // Check if we've reached max attempts.
    if (max_attempts > 0 && attempts >= max_attempts)
      return false;

    // Check for configured max attempts.
    int max = scheduler().server().config.max_attempts;
    if (max > 0 && attempts >= max)
      return false;

    return true;
  }

  /** Check if we can schedule the job. */
  public synchronized boolean canBeScheduled() {
    switch (status) {
      case failed:
        return hasMoreAttempts();
      case scheduled:
      case processing:
        return true;
      default:
        return false;
    }
  }

  /** Return whether or not the job has terminated. */
  public synchronized boolean isDone() {
    return done.filter().contains(status);
  }

  /**
   * Start the transfer between the specified resources. This returns a {@code
   * Bell} whose ringing indicates the completion of the job (or some other
   * problem encountered when starting the job).
   */
  public synchronized Bell<Job> start() {
    attempts++;
    try {
      Log.info("Starting job: ", this);
      return start0();
    } catch (Exception e) {
      Log.warning("Job failed: ", e);
      return new Bell<Job>(e);
    }
  }

  // Handle the actual starting the transfer. This method can throw any
  // exception it wants.
  private synchronized Bell<Job> start0() throws Exception {
    if (status != scheduled)
      throw new Exception("Job is not scheduled.");

    status(processing);

    // Keep this as a temporary in case we get unlucky and the job fails before
    // we return, because the done handler sets this.transfer to null.
    Transfer transfer =
      src.resolveAs("source").transferTo(dest.resolveAs("destination"));

    this.transfer = transfer;

    bytes = transfer.info;

    transfer.onStop().new Promise() {
      public void done() {
        // We did it! The transfer completed successfully.
        Log.info("Job complete: ", uuid());
        status(complete);
      } public void fail(Throwable t) {
        // There was some problem during the transfer. Reschedule if possible.
        Log.warning("Job failed: ", uuid(), " ", t);
        status(failed, t.getMessage());
        /*Delete: attempts++*/
        reschedule();
      }
    };

    // Wish me luck!
    transfer.start();

    return transfer.onStop().as(this);
  }

  public String toString() {
    return Ad.marshal(this).toString();
  }
}
