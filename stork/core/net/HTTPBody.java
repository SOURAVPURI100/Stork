package stork.core.net;

import java.net.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;

import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.socket.*;
import io.netty.handler.codec.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.*;
import io.netty.handler.stream.*;
import io.netty.util.*;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static io.netty.handler.codec.http.HttpMethod.*;

import stork.ad.*;
import stork.feather.*;
import stork.feather.URI;
import stork.feather.Path;
import stork.feather.util.*;
import stork.scheduler.*;

/**
 * A representation of an incoming HTTP request. This object provides accessors
 * for request metadata, as well as a tap/sink-based abstraction layer to the
 * Netty backend.
 */
public class HTTPBody extends Resource<HTTPRequest,HTTPBody> {
  protected URI uri;
  protected HttpResponseStatus status;  // The status we're going to report.
  public String contentType;
  public String location;

  /** Create an HTTPBody from the given HTTP request. */
  public HTTPBody(HTTPRequest req) {
    super(req);
  }

  /** Create an HTTPBody representing a body part. */
  protected HTTPBody(HTTPRequest req, Path path) {
    super(req, path);
  }

  /**
   * This will emit the content body as the data for the root resource.
   */
  public class HTTPTap extends Tap<HTTPBody> {
    HTTPTap() {
      super(HTTPBody.this);
    }

    public Bell start(Bell bell) {
      return bell.new Promise() {
        public void done() {
          session.ready = true;
          session.read();
        } public void fail() {
          // TODO
        }
      };
    }

    public Bell drain(Slice s) { return super.drain(s); }
    public void finish(Throwable t) { super.finish(t); }
  };

  /**
   * This will proxy data to the client. Data resources should be sent directly
   * to the client in the response body. TODO: Collection resources need to be
   * zipped or sent using some similar archival encoding. We can't use
   * multipart responses because not all browsers support it.
   */
  public class HTTPSink extends Sink<HTTPBody> {
    HTTPSink() { super(HTTPBody.this); }

    // If this is the root, send a header through Netty.
    public Bell start() {
      if (!destination().path.isRoot())
        throw new RuntimeException("Cannot send directories.");
      return generateHeader(source()).new As<HTTPBody>() {
        public HTTPBody convert(HttpResponse o) {
          session.toNetty(o);
          return HTTPBody.this;
        }
      };
    }

    public Bell drain(Slice slice) {
      ByteBuf buf = slice.asByteBuf();
      buf.retain();
      return session.toNetty(new DefaultHttpContent(buf));
    }

    public void finish(Throwable t) {
      session.finishResponse();
    }
  };

  /**
   * Generate an HTTP header for another resource based on its metadata.
   */
  private Bell<HttpResponse> generateHeader(Resource<?,?> resource) {
    return resource.stat().new As<HttpResponse>() {
      public HttpResponse convert(Stat stat) {
        if (stat.dir)
          throw new RuntimeException("Cannot send directories.");

        if (stat.size == 0) {
          HttpResponseStatus s = status == null ? NO_CONTENT : status;
          return new DefaultFullHttpResponse(session.version(), NO_CONTENT);
        }

        if (contentType == null) {
          contentType = MimeTypeMap.forFile(stat.name);
          if (contentType == null)
            contentType = "text/plain";
        }

        HttpResponseStatus st = status == null ? OK : status;
        HttpResponse r = new DefaultHttpResponse(session.version(), st);

        if (stat.size > 0)
          r.headers().set(CONTENT_LENGTH, stat.size);
        r.headers().set(CONTENT_TYPE, contentType);

        if (location != null) {
          r.headers().set(LOCATION, location);
        }

        if (stat.name != null) {
          String s = "inline; filename=\""+stat.name+"\"";
          r.headers().set("Content-Disposition", s);
        }

        return r;
      } public HttpResponse convert(Throwable t) throws Throwable {
        return errorToHttpMessage(t);
      }
    };
  }

  /** Create an HTTP response for a throwable. */
  private HttpResponse errorToHttpMessage(Throwable t) {
    String message = StorkInterface.errorToAd(t).get("message");
    ByteBuf b = Unpooled.copiedBuffer(message.getBytes());
    FullHttpResponse r = new DefaultFullHttpResponse(
      HTTP_1_1, INTERNAL_SERVER_ERROR, b);
    r.headers().set(CONTENT_TYPE, "text/plain");
    return r;
  }

  /** Return true if the tap can emit. */
  public boolean ready() {
    return session.ready;
  }

  /**
   * Get the length from the Content-Length header.
   */
  public long size() {
    try {
      return Long.parseLong(session.header("Content-Length"));
    } catch (Exception e) {
      return -1;
    }
  }

  public Bell<Stat> stat() {
    if (isRoot()) {
      Stat s = new Stat();
      s.name = "/";
      s.size = size();
      s.dir = session.isMultipart();
      s.file = !s.dir;
      return new Bell<Stat>().ring(s);
    } else {
      return null;  // TODO
    }
  }

  /** This will emit data coming from the client. */
  public Tap<HTTPBody> tap() {
    session.tap = new HTTPTap();
    return session.tap;
  }

  /** This will send data to the client. */
  public Sink<HTTPBody> sink() {
    return new HTTPSink();
  }
}
