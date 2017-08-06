package stork.core.net;

import io.netty.handler.codec.http.*;
import io.netty.buffer.*;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

/** An {@code Exception} wrapping an {@code HttpResponseStatus}. */
public class HTTPException extends RuntimeException {
  public final HttpResponseStatus status;

  public HTTPException(int status) {
    this((HttpResponseStatus) HttpResponseStatus.valueOf(status));
  }

  public HTTPException(HttpResponseStatus status) {
    super(status.toString());
    this.status = status;
  }

  public HttpMessage toHttpMessage() {
    ByteBuf b = Unpooled.copiedBuffer(getMessage().getBytes());
    FullHttpResponse r = new DefaultFullHttpResponse(HTTP_1_1, status, b);
    r.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
    r.headers().set(CONTENT_LENGTH, b.readableBytes());
    return r;
  }
}
