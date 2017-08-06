package stork.scheduler;

import stork.core.server.*;
import stork.util.*;
import stork.feather.*;
import stork.util.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Maintains a set of all {@code Job}s and handles ordering {@code Job} for
 * execution. This class implements a {@code Set} interface so its state can be
 * restored after restarting the system. {@code Job}s enter the system via the
 * {@link #add(Job)} method, which handles filtering and other bookkeeping.
 * Subclasses need only implement the {@link #schedule(Job)} method and any
 * data structures necessary to support the scheduling of {@code Job}s.
 */
public abstract class Scheduler implements Set<Job> {
  // All jobs known by the system, indexed by UUID.
  private transient HashMap<UUID,Job> jobs = new HashMap<UUID,Job>();

  // Jobs added before start() has been called.
  private transient List<Job> pending = new LinkedList<Job>();

  /**
   * Schedule {@code job} to be executed. The scheduler implementation need
   * only find a time to schedule the job based on whatever scheduling policy
   * the schedule implements. This will be called again automatically if a job
   * fails and it still has more attempts remaining.
   */
  protected abstract void schedule(Job job);

  /**
   * The server this scheduler belongs to. Subclasses should not override this.
   */
  public Server server() { return null; }

  /**
   * Add a job and schedule it if necessary. This will always either return
   * {@code true} or throw a {@code RuntimeException}.
   */
  public final synchronized boolean add(Job job) {
    if (contains(job))
      throw new RuntimeException("Job is already scheduled.");

    jobs.put(job.uuid(), job);

    job.scheduler = this;

    // If we're still waiting for start() to be called, add it to the pending
    // list.
    if (pending != null)
      pending.add(job);
    else
      doSchedule(job);

    return true;
  }

  private void doSchedule(Job job) {
    if (job.canBeScheduled()) try {
      job.status(JobStatus.scheduled);
      schedule(job);
    } catch (RuntimeException e) {
      job.status(JobStatus.failed, e.getMessage());
    }
  }

  /**
   * Call this to indicate that the server state has been finalized and jobs
   * may begin being scheduled.
   */
  public final synchronized void start() {
    for (Job job : pending)
      doSchedule(job);
    pending = null;
  }

  public final boolean addAll(Collection<? extends Job> jobs) {
    if (jobs.isEmpty())
      return false;
    for (Job job : jobs)
      add(job);
    return true;
  }

  /** Jobs cannot be removed. */
  public void clear() {
    throw new UnsupportedOperationException();
  }

  public final boolean contains(Object o) {
    if (o instanceof Job)
      return contains((Job) o);
    return false;
  }

  public final boolean contains(Job job) {
    return jobs.containsKey(job.uuid());
  }

  public final boolean containsAll(Collection<?> c) {
    for (Object o : c)
      if (!contains(c)) return false;
    return true;
  }

  public final boolean equals(Object o) {
    if (!(o instanceof Scheduler))
      return false;
    return containsAll((Scheduler) o);
  }

  /** Get a {@code Job} by its UUID. */
  public final Job get(UUID uuid) {
    return jobs.get(uuid);
  }

  public final int hashCode() {
    return jobs.hashCode();
  }

  public final boolean isEmpty() {
    return jobs.isEmpty();
  }

  public final Iterator<Job> iterator() {
    return jobs.values().iterator();
  }

  public final boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  public final boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  public final boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  public final int size() { return jobs.size(); }

  public final Object[] toArray() {
    return jobs.values().toArray(new Job[size()]);
  }

  public final <T> T[] toArray(T[] a) {
    return jobs.values().toArray(a);
  }
}
