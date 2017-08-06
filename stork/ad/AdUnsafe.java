package stork.ad;

import java.lang.reflect.*;

/**
 * A (semi-)cross-platform utility class for creating objects without calling
 * their constructor. This class is based on a similar class used in Google's
 * GSON library. It is anticipated that Java 8 will have a standardized way to
 * access {@code Unsafe}, in which case we might deprecate this class.
 */
final class AdUnsafe {
  private interface Allocator {
    Object create(Class c);
  }

  private static Allocator allocator = discover();

  private static Allocator discover() {
    // Try to find Sun's Unsafe class.
    try {
      AdType ut = new AdType(Class.forName("sun.misc.Unsafe"));
      final AdMember um = ut.method("allocateInstance", Class.class);
      final Object uo = ut.field("theUnsafe").get(null);
      return new Allocator() {
        public Object create(Class c) { return um.invoke(uo, c); }
      };
    } catch (Exception e) {}

    // Try to find pre-Gingerbread ObjectInputStream's newInstance.
    try {
      final AdMember um = new AdType(java.io.ObjectInputStream.class)
        .method("newInstance", Class.class, Class.class);
      if (um != null) return new Allocator() {
        public Object create(Class c) {
          return um.invoke(null, c, Object.class);
        }
      };
    } catch (Exception e) {}

    // Try to find post-Gingerbread ObjectInputStream's newInstance.
    try {
      AdType ois = new AdType(java.io.ObjectInputStream.class);
      final int ocid = (Integer) ois.method("getConstructorId", Class.class)
        .invoke(null, Object.class);
      final AdMember um = ois.method("newInstance", Class.class, int.class);
      if (um != null) return new Allocator() {
        public Object create(Class c) {
          return um.invoke(null, c, ocid);
        }
      };
    } catch (Exception e) {}

    // Guess we can't do unsafe creation...
    return null;
  }

  /**
   * Try to create a new instance of a class without invoking its constructor.
   *
   * @param c the class of the object to create
   * @return An unconstructed instance of {@code T}, or {@code null} if
   * unconstructed instantiation could not be performed.
   */
  public static synchronized <T> T create(Class<T> c) {
    return (allocator == null) ? null : c.cast(allocator.create(c));
  }
}
