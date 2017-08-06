package stork.feather.util;

import io.netty.buffer.*;

import stork.feather.*;

/**
 * A utility class for creating anonymous {@code Resource}s. An anonymous
 * {@code Resource} is one not identified by a URI. Unless otherwise specified,
 * all operations on the {@code Resource}s returned by this class are no-ops.
 */
public final class Resources {
  private Resources() { }

  /**
   * An anonymous {@code Resource}.
   */
  public static Resource anonymous() {
    return new Session(URI.EMPTY) {
      public Resource select(Path path) {
        return new Resource(this, path) { };
      }
    }.root();
  }

  /**
   * Get an anonymous {@code Resource} whose {@link Resource#stat()} will
   * return {@code stat}. This can be used to create, for instance, {@code
   * Resource}s with a known size.
   *
   * @param stat the {@code Stat} returned by the returned {@code Resource}'s
   * {@code stat()} method.
   * @return A {@code Resource} with the given {@code Stat}.
   */
  public static Resource anonymous(final Stat stat) {
    return new Session(URI.EMPTY) {
      public Resource select(Path path) {
        return new Resource(this, path) {
          public Bell<Stat> stat() {
            return new Bell<Stat>().ring(stat);
          }
        };
      }
    }.root();
  }

  /**
   * Get an anonymous empty data {@code Resource}.
   *
   * @return An anonymous {@code Resource} representing an empty data {@code
   * Resource}.
   */
  public static Resource empty() {
    return fromSlice(null);
  }

  /**
   * Get an anonymous empty collection {@code Resource}.
   *
   * @return An anonymous empty collection {@code Resource}.
   */
  public static Resource emptyCollection() {
    Stat stat = new Stat();
    stat.dir = true;
    return anonymous(stat);
  }

  /**
   * Get an anonymous data {@code Resource} with a {@code Stat} based on {@code
   * slice} and whose {@code Tap} emits {@code slice}.
   */
  public static Resource fromSlice(final Slice slice) {
    final Stat stat = new Stat();
    stat.file = true;
    stat.time = Time.now();
    stat.size = (slice != null) ? slice.length() : 0;

    if (slice.offset() > 0)
      stat.size += slice.offset();

    return new Session(URI.EMPTY) {
      public Resource select(Path path) {
        return new Resource(this, path) {
          public Bell<Stat> stat() {
            return new Bell<Stat>(stat);
          } public Tap tap() {
            return Pipes.tapFromSlice(this, slice);
          }
        };
      }
    }.root();
  }
}
