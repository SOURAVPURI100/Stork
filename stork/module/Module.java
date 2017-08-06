package stork.module;

import stork.ad.*;
import stork.feather.*;
import stork.util.*;
import stork.scheduler.*;

import java.util.*;

/**
 * Abstract base class for a Stork transfer module.
 *
 * @param <R> The {@code Resource} type.
 */
public abstract class Module<R extends Resource> {
  private String name = "Untitled", handle = "untitled";
  private String[] protocols = new String[0];
  private String description = "(no description)";

  /** Get the module name. */
  public final String name() { return name; }

  /** Get the module handle (i.e., short name). */
  public final String handle() { return handle; }

  /** Get the description. */
  public final String description() { return description; }

  /** Get the supported protocols. */
  public final String[] protocols() { return protocols; }

  /** Set the module name. */
  protected final void name(String name) {
    if (name == null) return;
    name = name.trim();
    if (name.isEmpty()) return;
    this.name = name;
    this.handle = StorkUtil.normalize(name);
  }

  /** Set the supported protocols. */
  protected final void protocols(String... protocols) {
    if (protocols.length == 0)
      this.protocols = null;
    else
      this.protocols = normalizedSet(protocols);
  }

  /** Set the description. Arguments will be joined with space. */
  protected final void description(String... description) {
    this.description = StorkUtil.join((Object[]) description);
  }

  // Return a normalized string set. Used for protocol and option sets.
  private static String[] normalizedSet(String... s) {
    for (int i = 0; i < s.length; i++)
      s[i] = StorkUtil.normalize(s[i]);
    return new HashSet<String>(Arrays.asList(s)).toArray(new String[0]);
  }

  /**
   * Return a handle on the resource identified by a URI.
   *
   * @param uri a string representation of the URI to select.
   * @return A handle on the resource identified by a URI.
   * @throws IllegalArgumentException if the given URI does not properly
   * identify a resource or specifies a scheme not supported by this module.
   */
  public final R select(String uri) {
    return select(uri, null);
  }

  /**
   * Return a handle on the resource identified by a URI using the given
   * authentication factor.
   *
   * @param uri a string representation of the URI to select.
   * @param credential an authentication factor, or {@code null}.
   * @return A handle on the resource identified by a URI.
   * @throws IllegalArgumentException if the given URI does not properly
   * identify a resource or specifies a scheme not supported by this module.
   */
  public final R select(String uri, Credential credential) {
    return select(URI.create(uri), credential);
  }

  /**
   * Return a handle on the resource identified by a URI.
   *
   * @param uri the URI to select.
   * @return A handle on the resource identified by a URI.
   * @throws IllegalArgumentException if the given URI does not properly
   * identify a resource or specifies a scheme not supported by this module.
   */
  public final R select(URI uri) {
    return select(uri, null);
  }

  /**
   * Return a handle on the resource identified by a URI using the given
   * authentication factor. Subclasses should implement this to handle the
   * module-specific details of interpretting the URI and instantiating a
   * {@link Resource} (and its underlying {@link Session}) to access the
   * resource indentified by the URI.
   *
   * @param uri the URI to select.
   * @param credential an authentication factor, or {@code null}.
   * @return A handle on the resource identified by a URI.
   * @throws IllegalArgumentException if the given URI does not properly
   * identify a resource or specifies a scheme not supported by this module.
   * @see Resource
   * @see Session
   */
  public abstract R select(URI uri, Credential credential);

  public String toString() { return handle; }
}
