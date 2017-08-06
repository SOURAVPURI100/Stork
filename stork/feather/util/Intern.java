package stork.feather.util;

import java.lang.ref.*;
import java.util.*;

/**
 * A object internalization utility based on soft references. The use of soft
 * references allows objects to be kept until the garbage collector needs to
 * make some room. This class should only be used for read-only objects, as
 * interned objects are shared.
 */
public class Intern<O> {
  private Map<O, SoftReference<O>> map =
    new WeakHashMap<O, SoftReference<O>>();

  private static Intern<String> STRING_INTERN = new Intern<String>();

  /** Globally intern a string. */
  public static String string(String s) {
    return STRING_INTERN.intern(s);
  }

  /**
   * Return a canonical reference to an object. If the object is not present in
   * the intern map, it becomes the canonical reference. Otherwise the
   * canonical internalized reference is returned.
   */
  public synchronized O intern(O k) {
    if (k == null)
      return null;

    SoftReference<O> s = map.get(k);

    if (s == null || s.get() == null) {
      map.put(k, s = new SoftReference<O>(k));
    }

    return s.get();
  }
}
