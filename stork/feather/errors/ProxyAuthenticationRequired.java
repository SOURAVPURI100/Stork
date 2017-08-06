package stork.feather.errors;

/**
 * Convey the need for authorization to access a protected endpoint. This
 * should be used when a proxied resource (such as a file on a remote server)
 * needs credentials to be access. This should not be used if an API endpoint
 * needs authorization for some operation. For that, use {@link
 * AuthorizationRequired}.
 */
public class ProxyAuthenticationRequired extends FeatherError {
  /** Acceptable credential types. */
  public String[] options;

  /**
   * Authentication is required. Optionally, a list of allowed credential types
   * can be provided. These will be reported back to the client so it can
   * display authentication options.
   */
  public ProxyAuthenticationRequired(String... options) {
    super("Proxy authentication is required.");
    if (options != null && options.length > 0)
      this.options = options;
  }
}
