package stork.module.ftp;

import java.text.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;

import io.netty.buffer.*;

import stork.feather.*;
import stork.module.*;
import stork.util.*;

/**
 * A class for parsing FTP listings. Based heavily on Mozilla's own FTP list
 * parsing code, available in their mozilla-central repository, under the path:
 * <p/>
 *   netwerk/streamconv/converters/ParseFTPList.cpp
 * <p/>
 * This parser will return a tree root that has its name set if and only if
 * information about the listed directory was able to be retrieved from the
 * listing results.
 */
public class FTPListParser extends Bell<Stat> {
  private String data = null;
  private int list_type;
  private StringBuilder sb = new StringBuilder();
  protected Stat root;
  private List<Stat> files = new LinkedList<Stat>();

  // Create a parser with an optional known type suggestion.
  public FTPListParser() {
    this(null, 0);
  } public FTPListParser(int type) {
    this(null, type);
  } public FTPListParser(Stat r, int type) {
    root = (r != null) ? r : new Stat();
    list_type = type;
  }

  // Check if a file should be ignored.
  public boolean ignoreName(String name) {
    return name == null || name.equals(".") || name.equals("..");
  }

  // Split the data so that each entry can be parsed individually. Only
  // feed this thing complete lines.
  private static Pattern line_pattern =
    Pattern.compile("[\\s\\00]*([^\\n\\r\\00]+)");
  private void parseData(CharSequence data) {
    Matcher m = line_pattern.matcher(data);

    // Keep finding lines.
    while (m.find())
      parseLine(m.group(1));
  }

  // Parse a single line.
  public void parseLine(String line) {
    Stat stat = parseEntry(line);
    if (stat == null || stat.name == null)
      return;
    if (stat.name.equals("."))
      root.copy(stat);
    else if (!ignoreName(stat.name))
      files.add(stat);
  }

  // Set the name of the root.
  public void name(String name) {
    root.name = name;
  }

  // This allows the entire list to be read in one shot.
  public void parseAll(byte[] b) {
    write(b);
    finish();
  }

  // Finalize the parser and get the sorted ads. Any more calls to this
  // thing will exhibit undefined behavior.
  public void finish() {
    // Parse any buffered data.
    if (sb.length() > 0) {
      parseData(sb);
      sb = null;
    } ring(root.setFiles(files));
  }

  // Write a byte buffer to the file, decode as string, scan for newlines, and
  // feed lines through parser. Presumably we're reading data where newlines
  // are one byte so just look for newline characters.
  public void write(ByteBuf b) {
    byte[] bytes;
    if (b.hasArray())
      bytes = b.array();
    else
      b.getBytes(0, bytes = new byte[b.readableBytes()]);
    write(bytes);
  } public void write(byte[] b) {
    // Find last newline and chomp it, then buffer the rest.
    int o;
    out: for (o = b.length-1; o >= 0; o--) switch (b[0]) {
      case '\r':
      case '\n': break out;
    } if (o >= 0) {  // If we found something...
      parseData(sb.append(new String(b, 0, o+1)));
      sb = new StringBuilder();
      if (o != b.length-1)
        sb.append(new String(b, o+1, b.length-o));
    } else {  // Otherwise buffer the string.
      sb.append(new String(b));
    }
  }

  // Parse a line from the listing.
  public Stat parseEntry(String line) {
    String[] tokens;
    Stat stat = new Stat();

    if (line.isEmpty())
      return null;

    // Split the line into tokens delimited by whitespace.
    tokens = line.split("\\s+");

    // If we have a cached listing type, jump to the right parser.
    switch (list_type) {
      case 0:  // Unknown type, just check them all.

      case 'E':  // Check for an EPLF listing.
      if (tokens.length >= 2 && tokens[0].startsWith("+")) try {
        // We should tokenize on tab.
        String[] t     = line.substring(1).split("\t+", 2);
        String[] facts = t[0].split(",+");
        String name    = t[1];

        // Parse facts according to prefixes.
        for (String f : facts) if (!f.isEmpty()) {
          switch (f.charAt(0)) {
            case 'm':  // Modification time.
              stat.time = Long.parseLong(f.substring(1)); break;
            case '/':  // It's a directory.
              stat.dir = true; break;
            case 'r':  // It's a file.
              stat.file = true; break;
            case 's':  // Size.
              stat.size = Long.parseLong(f.substring(1)); break;
            case 'u':  // Permissions.
              if (f.charAt(1) == 'p')
                stat.perm = f.substring(2);
          }
        }

        // Everything else after the tab is the file name.
        stat.name = name;
        list_type = 'E';

        return stat;
      } catch (Exception e) {
        // Bad formatting, skip.
        if (list_type != 0) break;
      }

      case 'M':  // Check for an MLSX listing.
      if (tokens.length >= 2) try {
        // We must trim leading and tokenize on a single space.
        String[] t = line.replaceAll("^\\s+", "").split(" ", 2);
        String[] facts = t[0].split(";+");
        String name = t[1];

        if (t.length != 2)
          throw null;

        // Parse each fact, splitting at =.
        for (String f : facts) {
          String s[] = f.split("=", 2);
          String perm = null;
          boolean unix = false;
          s[0] = s[0].toLowerCase();

          if (s.length != 2)
            throw null;

          if (s[0].length() < 4) {
            continue;
          } if (s[0].equals("type")) {
            if (s[1].equalsIgnoreCase("file"))
              stat.file = true;
            else if (s[1].equalsIgnoreCase("dir"))
              stat.dir = true;
            else if (s[1].equalsIgnoreCase("cdir"))
              stat.dir = true;
            else if (s[1].equalsIgnoreCase("pdir"))
              stat.dir = true;
            else  // It's just something weird. Let's call it a file.
              stat.file = true;
          } else if (s[0].equals("modify")) {
            DateFormat df = new SimpleDateFormat("yyyyMMDDHHmmss");
            stat.time = df.parse(s[1]).getTime()/1000;
          } else if (s[0].equals("size")) {
            stat.size = Long.parseLong(s[1]);
          } else if (s[0].equals("unix.mode")) {
            int p = Integer.parseInt(s[1], 8);
            unix = true;
            perm = new String(new char[] {
              (0 == (p & 0400)) ? '-' : 'r',
              (0 == (p & 0200)) ? '-' : 'w',
              (0 == (p & 0100)) ? '-' : 'x',
              (0 == (p & 0040)) ? '-' : 'r',
              (0 == (p & 0020)) ? '-' : 'w',
              (0 == (p & 0010)) ? '-' : 'x',
              (0 == (p & 0004)) ? '-' : 'r',
              (0 == (p & 0002)) ? '-' : 'w',
              (0 == (p & 0001)) ? '-' : 'x' });
          } else if (s[0].equals("perm") && !unix) {
            perm = s[1];
          } if (perm != null) {
            if (unix) perm = (stat.dir?'d':'-')+perm;
            stat.perm = perm;
          }
        }

        // Everything else after the tab is the file name.
        stat.name = name;

        list_type = 'M';
        return stat;
      } catch (Exception e) {
        // Bad formatting, skip.
        if (list_type != 0) break;
      }

      case 'V':  // TODO: Check for a VMS listing.
      case 'C':  // TODO: Check for a CMS listing.
      case 'W':  // TODO: Check for a Windows listing.
      case 'O':  // TODO: Check for an OS2 listing.

      case 'U':  // Check for a Unix listing.
      if (tokens.length >= 6) try {
        String perm = tokens[0], name, link = null;
        long time, size;
        boolean dir = false;

        // Check for permission flags.
        if (perm.length() != 10 && perm.length() != 11)
          throw null;
        if (!perm.matches("[-bcdlpsw?DFam]([-r][-w].){3}.?"))
          throw null;

        // Scan for size token.
        int i;  // Index of size token.
        for (i = tokens.length-5; i > 1; i--) {
          if (tokens[i].matches("[0-9]+"))
          if (tokens[i+1].matches("[A-Za-z]{3}"))
          if (tokens[i+2].matches("[0-9]{1,2}"))
          if (tokens[i+3].matches("[0-9]{4}|[0-9]{1,2}(:[0-9]{2}){1,2}"))
            break;
        } if (i > 1) {
          // Check if it's a directory.
          dir = perm.charAt(0) == 'd' || perm.charAt(0) == 'D';

          // Parse size.
          try {
            size = Long.parseLong(tokens[i]);
          } catch (Exception e) {
            size = -1;
          }

          // Parse time.
          try {
            String d = tokens[i+1]+" "+tokens[i+2]+" "+tokens[i+3];
            if (tokens[i+3].indexOf(":") > 0) {
              DateFormat df = new SimpleDateFormat("MMM d H:mm");
              Calendar stupid = Calendar.getInstance();
              int year = stupid.get(Calendar.YEAR);
              stupid.setTime(df.parse(d));
              stupid.set(Calendar.YEAR, year);
              Calendar calendar = Calendar.getInstance();
              time = stupid.getTimeInMillis()/1000;
            } else {
              DateFormat df = new SimpleDateFormat("MMM d yyyy");
              time = df.parse(d).getTime()/1000;
            }
          } catch (Exception e) {
            time = -1;
          }

          // Kind of silly thing to get the name.
          try {
            String[] t = line.split("\\s+", i+4);
            t = t[t.length-1].split("\\s", 2);
            name = t[1];

            // Fix symlink names.
            if (perm.charAt(0) == 'l') {
              if (name.endsWith("/"))
                dir = true;
              String[] names = name.split(" -> ");
              name = names[0];
              link = names[1];
            }
          } catch (Exception e) {
            name = tokens[tokens.length-1];
          }

          if (stat != root && !ignoreName(name))
            stat.name = name;
          if (time > 0)
            stat.time = time;
          if (size > 0 && !dir)
            stat.size = size;
          stat.dir = dir;
          stat.file = !dir;
          stat.perm = perm;
          stat.link = link;

          list_type = 'U';
          return stat;
        }
      } catch (Exception e) {
        // Bad formatting, skip.
        if (list_type != 0) break;
      }

      case 'w':  // TODO: Check for a Windows 16-bit listing.
      case 'D':  // TODO: Check for a /bin/dls listing.
    } return null;
  }
}
