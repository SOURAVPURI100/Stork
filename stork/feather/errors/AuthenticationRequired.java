package stork.feather.errors;

public class AuthenticationRequired extends FeatherError {
  /** Acceptable credential types. */
  public String[] options;

  /**
   * Authentication is required. Optionally, a list of allowed credential types
   * can be provided. These will be reported back to the client so it can
   * display authentication options.
   */
  public AuthenticationRequired(String... options) {
    super("Authentication is required.");
    if (options != null && options.length > 0)
      this.options = options;
  }
}
