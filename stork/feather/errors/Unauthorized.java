package stork.feather.errors;

/**
 * Require authorization for access to a resource. This should be used to
 * request credentials for access to a resource.
 */
public class Unauthorized extends FeatherError {
  public Unauthorized() {
    super("Authorization is required.");
  }
}
