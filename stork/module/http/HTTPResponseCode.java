package stork.module.http;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Collection of functions that tests HTTP response code.
 */
public class HTTPResponseCode {

  /**
   * Tells if HTTP response server has moved its {@code URL}.
   * 
   * @param status instance of {@code HttpResponseStatus}
   * @return {@code true} if {@code URL} is moved; otherwise,
   * {@code false}
   */
  public static boolean isMoved(HttpResponseStatus status) {
    if (status.equals(HttpResponseStatus.FOUND) ||
        status.equals(HttpResponseStatus.MOVED_PERMANENTLY) ||
        status.equals(HttpResponseStatus.TEMPORARY_REDIRECT)) {
      return true;
    } else {
      return false;
    }
  }
  
  /**
   * Tells if HTTP response server is not found.
   * 
   * @param status instance of {@code HttpResponseStatus}
   * @return {@code true} if page is not found; otherwise,
   * {@code false}
   */
  public static boolean isNotFound(HttpResponseStatus status) {
    if (status.equals(HttpResponseStatus.NOT_FOUND)) {
      return true;
    } else {
      return false;
    }
  }
  
  /**
   * Tells if HTTP response server failed to accept the request.
   * 
   * @param status instance of {@code HttpResponseStatus}
   * @return {@code true} if it is bad request; otherwise,
   * {@code false}
   */
  public static boolean isInvalid(HttpResponseStatus status) {
    if (status.equals(HttpResponseStatus.BAD_REQUEST) ||
        status.equals(HttpResponseStatus.NOT_IMPLEMENTED)) {
      return true;
    } else {
      return false;
    }
  }
  
  /**
   * Tells if HTTP response server successfully accepts the request.
   * 
   * @param status instance of {@code HttpResponseStatus}
   * @return {@code true} if it accepts; otherwise, {@code false}
   */
  public static boolean isOK(HttpResponseStatus status) {
    if (status.equals(HttpResponseStatus.OK)) {
      return true;
    } else{
      return false;
    }
  }
}
