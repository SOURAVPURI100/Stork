package stork.module.http;

/** Exception class for HTTP module. */
public class HTTPException extends Exception {

  private static final long serialVersionUID = 1L;

  public HTTPException (String message) {
    super (message);
  }
}
