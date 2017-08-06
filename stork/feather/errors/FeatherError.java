package stork.feather.errors;

public abstract class FeatherError extends RuntimeException {
  public FeatherError(String reason) {
    super(reason);
    if (reason == null)
      throw new NullPointerException();
  }
}
