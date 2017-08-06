package stork.feather;

/**
 * An abstract authentication factor for authenticating with a remote system.
 */
public abstract class Credential<O> {
  /**
   * Get the raw credential token wrapped by this object.
   *
   * @return The raw credential token wrapped by this object.
   */
  public abstract O data();

  /**
   * Get the expiration time of the credential in Unix time (milliseconds), or
   * 0 if the credential will never expire.
   *
   * @return The expiration time of the credential.
   */
  public long duration() {
    return 0;
  }
}
