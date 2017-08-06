package stork.cred;

import java.util.*;

import stork.ad.*;
import stork.feather.*;
import stork.scheduler.*;
import stork.util.*;

/** An extended Feather credential with additional metadata. */
public abstract class StorkCred<O> extends Credential<O> {
  /** The human-readable type of the credential. */
  public String type;
  /** A user-given name for this credential. */
  public String name = "(unnamed)";
  /** Cached hash code. */
  private transient int hashCode = -1;

  public StorkCred(String type) { this.type = type; }

  public static StorkCred newFromType(String type) {
    if (type.equals("userinfo"))
      return new StorkUserinfo();
    if (type.equals("gss"))
      return new StorkGSSCred();
    if (type.equals("oauth"))
      return new StorkOAuthCred(null);
    throw new RuntimeException("Unknown credential type.");
  }

  // Get an info object suitable for showing to users. It should not include
  // sensitive information.
  public Object getInfo() {
    return new Object() {
      String name = StorkCred.this.name;
      String type = StorkCred.this.type;
    };
  }

  /** Return an array of objects that make up this credential's identity. */
  protected abstract Object[] hashables();

  /** Check if two credentials' "hashables" are array-equal. */
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null)
      return false;
    if (o.getClass() != getClass())
      return false;
    StorkCred cred = ((StorkCred) o);
    if (!type.equals(cred.type))
      return false;
    return Arrays.equals(hashables(), cred.hashables());
  }

  /** Return the hash code of credential's "hashables". */
  public int hashCode() {
    if (hashCode == -1)
      hashCode = Arrays.hashCode(hashables());
    return hashCode;
  }
}
