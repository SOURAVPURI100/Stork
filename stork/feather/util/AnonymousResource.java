package stork.feather.util;

import stork.feather.*;

/** A base class for {@code Resource}s with no {@code Session}. */
public abstract class AnonymousResource extends Resource {
  /** Create an {@code AnonymousResource} at the root {@code Path}. */
  public AnonymousResource() { this(Path.ROOT); }

  /** Create an {@code AnonymousResource} at {@code path}. */
  public AnonymousResource(Path path) {
    super(new AnonymousSession(), path);
    ((AnonymousSession)session).root = this;
  }

  /**
   * Subclasses must override this to fulfill the role of {@code
   * Session.select()} for producing sub-{@code Resource}s.
   */
  public abstract Resource select(Path path);
}

class AnonymousSession extends Session {
  AnonymousResource root;

  AnonymousSession() { super(URI.EMPTY); }

  public Resource select(Path path) {
    return root.select(path);
  }
}
