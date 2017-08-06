package stork.util;

import java.util.*;
import java.util.regex.*;
import java.io.*;

import stork.ad.Ad;

// A bunch of static utility functions, how fun!
//
// TODO: Most of these things were placed here because they provided
// common utility that wasn't available elsewhere. Some of this utility
// may be better suited being taken out of here and made into another
// class, so see if there's anything we should do that with.

public abstract class StorkUtil {
  // Some pre-compiled regexes.
  public static final Pattern
    regex_ws   = Pattern.compile("\\s+"),
    regex_csv  = Pattern.compile("\\s*(,\\s*)+"),
    regex_norm = Pattern.compile("[^a-z_0-9\\Q-_+,.\\E]+"),
    regex_path = Pattern.compile("[^^]/+");

  // Static imports
  // --------------
  // Static methods with small names meant to be statically imported
  // into classes
  public static class Static {
    // Join objects into a string.
    public static String J(Object... sa) {
      return join(sa);
    }

    // Join objects into a string with delimiter.
    public static String JW(String d, Object... sa) {
      return joinWith(d, sa);
    }

    // Print debugging output.
    public static void D(Object... o) {
      Log.fine(o);
    }
  }

  // String functions
  // ----------------
  // All of these functions should take null and treat it like "".

  // Normalize a string by lowercasing it, replacing spaces with _,
  // and removing characters other than alphanumerics or: -_+.,
  public static String normalize(String s) {
    if (s == null) return "";

    s = s.toLowerCase();
    s = regex_norm.matcher(s).replaceAll(" ").trim();
    s = regex_ws.matcher(s).replaceAll("_");

    return s;
  }

  // Split a CSV string into an array of normalized strings.
  public static String[] splitCSV(String s) {
    if (s == null) s = "";
    String[] a = regex_csv.split(s, 0);
    for (int i = 0; i < a.length; i++)
      a[i] = normalize(a[i]);
    return (a == null) ? new String[0] : a;
  }

  // Collapse a string array back into a CSV string.
  public static String joinCSV(Object... sa) {
    return joinWith(", ", sa);
  }

  // Join a string with spaces.
  public static String join(Object... sa) {
    return joinWith(" ", sa);
  } 

  // Join a string array with a delimiter.
  public static String joinWith(String del, Object... sa) {
    StringBuffer sb = new StringBuffer();
    
    if (del == null) del = "";

    if (sa != null && sa.length != 0) {
      sb.append(sa[0]);
      for (int i = 1; i < sa.length; i++)
        if (sa[i] != null) sb.append(del+sa[i]);
    } return sb.toString();
  }

  // Wrap a paragraph to some number of characters.
  public static String wrap(String str, int w) {
    StringBuffer sb = new StringBuffer();
    String line = "";

    for (String s : regex_ws.split(str)) {
      if (!line.isEmpty() && line.length()+s.length() >= w) {
        if (sb.length() != 0) sb.append('\n');
        sb.append(line);
        line = s;
      } else {
        line = (line.isEmpty()) ? s : line+' '+s;
      }
    }

    if (!line.isEmpty()) {
      if (sb.length() != 0) sb.append('\n');
      sb.append(line);
    }

    return sb.toString();
  }

  // Path functions
  // --------------
  // Functions that operate on path strings. Like string functions, should
  // treat null inputs as an empty string.

  // Split a path into its components. The first element will be a slash
  // if it's an absolute path, and the last element will be an empty
  // string if this path represents a directory.
  public static String[] splitPath(String path) {
    return regex_path.split((path != null) ? path : "", -1);
  }

  // Get the basename from a path string.
  public static String basename(String path) {
    if (path == null) return "";

    path = path.replaceAll("/+$", "");
    int i = path.lastIndexOf('/');

    return (path.isEmpty()) ? "/"  :
           (i == -1)        ? path : path.substring(i+1);
  }

  // Get the dirname from a path string, including trailing /.
  public static String dirname(String path) {
    if (path == null) return "";

    path = path.replaceAll("/+$", "");
    int i = path.lastIndexOf('/');

    return (path.isEmpty()) ? "/"  :
           (i == -1)        ? path : path.substring(0, i);
  }

  // File system functions
  // ---------------------
  // Functions to get information about the local file system.
  
  // Get the size of a file.
  public static long size(String path) throws Exception {
    return new File(path).length();
  }

  // Miscellaneous functions
  // -----------------------
  // Convert a size into a human-readable string.
  public static String prettySize(long s) {
    return prettySize((double)s, (char)0);
  } public static String prettySize(double s) {
    return prettySize(s, (char)0);
  } private static String prettySize(double s, char pre) {
    if (s >= 1000) switch (pre) {
      // Uppercase characters for base 10.
      case  0 : return prettySize(s/1000, 'k');
      case 'k': return prettySize(s/1000, 'M');
      case 'M': return prettySize(s/1000, 'G');
      case 'G': return prettySize(s/1000, 'T');
    } if (pre == 0) {
      return String.format("%d", (int) s);
    } return String.format("%.02f%c", s, pre);
  }

  // Convert a byte array into a formatted string.
  public static String formatBytes(byte[] bytes, String fmt) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes)
      sb.append(String.format(fmt, b));
    return sb.toString();
  }

  // Read a file into a string.
  public static String readFile(String path) {
    return readFile(new File(path));
  } public static String readFile(File f) {
    try {
      return new Scanner(f, "UTF-8").useDelimiter("\\A").next();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
