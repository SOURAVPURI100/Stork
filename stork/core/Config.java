package stork.core;

import java.io.*;

import stork.ad.*;
import stork.feather.*;
import stork.util.*;

/** A class for storing configuration settings. */
public class Config {
  // Default locations for stork.conf.
  private static String[] defaultPaths = {
    System.getenv("STORK_CONFIG"),
    System.getProperty("stork.config"),
    System.getProperty("stork.exedir", ".")+"/stork.conf",
    "./stork.conf",
    "../stork.conf",
    "/usr/share/stork/etc/stork.conf"
  };

  /** Global configuration. */
  public static final Config global = loadConfig();

  public int max_jobs = 10;
  public int max_attempts = 10;
  public int max_history = 10;

  //public String libexec = "libexec";

  public String state_file = null;
  public int state_save_interval = 120;

  public URI connect = URI.create("tcp://localhost:57024");
  public URI[] listen;
  public URI web_service_url;

  public boolean registration = true;

  public double request_timeout = 5.0;

  public String email = "StorkCloud <noreply@storkcloud.org>";
  public String smtp_server = "localhost";

  /** Dropbox configuration. */
  public stork.staging.DbxOAuthSession.DropboxConfig dropbox;

  // Check default paths until we find a readable file. Null if none found.
  private static String defaultConfig() {
    for (String path : defaultPaths)
      if (path != null && canAccessPath(path)) return path;
    return null;
  }

  // Check that path is readable and return absolutized file, else null.
  private static boolean canAccessPath(String path) {
    try {
      return new File(path).getAbsoluteFile().canRead();
    } catch (Exception e) {
      return false;
    }
  }

  // Parse config file, where each line is either a comment or an
  // Ad expression which gets merged into the returned ad.
  private static Config parseConfig(String path) {
    path = (path != null) ? path : defaultConfig();

    //Log.info("Loading config from path: ", path);

    // Error checking
    if (!canAccessPath(path)) {
      if (path != null)
        throw new RuntimeException("Couldn't open '"+path+"'");
      throw new RuntimeException("STORK_CONFIG not set and "+
              "couldn't find stork.conf in default locations");
    }

    return Ad.parse(new File(path), true).unmarshalAs(Config.class);
  }

  /** Find the config file, open and parse it, and unmarshal settings. */
  public static Config loadConfig(String path) {
    return parseConfig(path);
  }

  /** Load the default config file. */
  public static Config loadConfig() {
    return parseConfig(null);
  }

  public String toString() {
    return Ad.marshal(this).toString();
  }
}
