package stork.core.server;

import java.io.*;

import stork.ad.*;
import stork.core.*;
import stork.util.*;

/**
 * Daemon thread which dumps server state periodically, or can be forced to
 * dump the server state.
 */
public class DumpStateThread extends Thread {
  private boolean dead = false;
  private Object object;
  private Config config;

  public DumpStateThread(Config config, Object object) {
    super("server dump thread");
    setDaemon(true);
    this.object = object;
    this.config = config;
    start();
    Log.info("Starting server state dump thread.");
  }

  public void kill() {
    dead = true;
    interrupt();
  }

  public void run() {
    while (!dead) {
      int delay = config.state_save_interval;
      if (delay < 1) {
        delay = new Config().state_save_interval;
        Log.warning("state_save_interval must be positive.");
        Log.warning("Setting state_save_interval to: ", delay, "s");
      }

      // Wait for the delay, then dump the state. Can be interrupted to dump
      // the state early.
      try {
        sleep(delay*1000);
      } catch (Exception e) {
        // Continue on.
      } if (!dead) {
        internalDumpState();
      }
    }
  }

  public synchronized void setConfig(Config config) {
    this.config = config;
  }

  /** Dump the state ahead of schedule. */
  public synchronized void dumpState() { interrupt(); }

  // Dump the state to the state file.
  private synchronized void internalDumpState() {
    String state_path = config.state_file;
    File state_file = null, temp_file = null;
    PrintWriter pw = null;

    if (state_path != null) try {
      state_file = new File(state_path).getAbsoluteFile();

      // Some initial sanity checks.
      if (state_file.exists()) {
        if (state_file.exists() && !state_file.isFile())
          throw new RuntimeException("State file is a directory.");
        if (!state_file.canWrite())
          throw new RuntimeException("Cannot write to state file.");
      }

      temp_file = File.createTempFile(
          ".stork_state", "tmp", state_file.getParentFile());
      pw = new PrintWriter(temp_file, "UTF-8");

      pw.print(Ad.marshal(object).toJSON());
      pw.close();
      pw = null;

      if (!temp_file.renameTo(state_file))
        throw new RuntimeException("Could not rename temp dump file.");
    } catch (Exception e) {
      Log.warning("Couldn't save state: "+
          state_file+": "+e.getMessage());
    } finally {
      if (temp_file != null && temp_file.exists()) {
        temp_file.delete();
      } if (pw != null) try {
        pw.close();
      } catch (Exception e) {
        // Ignore.
      }
    }
  }
}
