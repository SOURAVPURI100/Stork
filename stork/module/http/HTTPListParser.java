package stork.module.http;

import java.io.*;
import java.util.*;

import org.jsoup.*;
import org.jsoup.nodes.*;

import stork.feather.*;
import stork.feather.util.*;

/**
 * Extracts links from HTML pages.
 */
public class HTTPListParser {
  private final InputStream is;
  private final URI base;
  private Bell<List<Stat>> listBell;

  /** Extract a listing from {@code source}. This starts {@code source}. */
  public HTTPListParser(URI base, Tap source) {
    this.base = base;
    is = Pipes.asInputStream(source);
    source.start();
  }

  /** Parse and return the listing. */
  public synchronized Bell<List<Stat>> getListing() {
    if (listBell == null) listBell = new ThreadBell<Set<String>>() {
      public Set<String> run() throws Exception {
        Document doc = Jsoup.parse(is, "UTF-8", base.toString());
        Set<String> names = new HashSet<String>();

        for (Element e : doc.select("a[href]")) {
          addName(names, e.attr("href"));
        } for (Element e : doc.select("[src]")) {
          addName(names, e.attr("src"));
        } for (Element e : doc.select("link[href]")) {
          addName(names, e.attr("href"));
        }
        return names;
      }
    }.start().new As<List<Stat>>() {
      public List<Stat> convert(Set<String> names) {
        List<Stat> stats = new LinkedList<Stat>();
        for (String name : names) {
          Stat stat = createStat(name);
          if (stat != null)
            stats.add(stat);
        }
        return stats;
      }
    };
    return listBell.detach();
  }

  /** Add a relative path. */
  private void addName(Set<String> names, String name) {
    if (name.startsWith("#"))
      return;
    URI uri = URI.create(name);
    if (uri.isAbsolute())
      return;
    Path path = uri.path();
    if (!path.isRoot() && path.up().equals(base.path()))
      names.add(path.name());
  }

  /** Create a Stat from a name. */
  private static Stat createStat(String name) {
    boolean dir = name.endsWith("/");
    if (dir)
      name = name.replaceAll("/+$", "");
    if (name.isEmpty())
      return null;
    Stat stat = new Stat(name);
    stat.dir = dir;
    stat.file = !dir;
    return stat;
  }
}
