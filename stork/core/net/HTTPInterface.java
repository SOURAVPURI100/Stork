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
import stork.feather.util.*;
import stork.feather.URI;
import stork.feather.Path;
import stork.feather.errors.*;
import stork.core.server.*;

/**
 * A basic HTTP interface to tie the Stork server into the HTTP server.
 */
public class HTTPInterface extends StorkInterface {
  private final String host;
  private final int port;

  private static Map<URI, HTTPInterface> interfaces =
    new HashMap<URI, HTTPInterface>();

  public HTTPInterface(Server s, URI uri) {
    super(s);

    host = (uri.host() != null) ? uri.host() : "localhost";
    port = uri.port();

    new HTTPServer.Route(uri, "GET", "POST") {
      public void handle(HTTPRequest request) {
        handleRequest(request);
      }
    };
  }

  public String name() { return "HTTP"; }

  public String address() {
    String a = host;
    if (port > 0) a += ":"+port;
    return a;
  }

  // Issue the request and relay the response to the client.
  private void handleRequest(final HTTPRequest hr) {
    fillRequestForm(hr).new Promise() {
      public void done(Request request) {
        issueRequest(request);
        request.promise(requestDoneHandler((HTTPBody)request.resource));
      }
    };
  }

  // This handles whatever happens when a request has completed.
  private Bell<Object> requestDoneHandler(final HTTPBody body) {
    return new Bell<Object>() {
      public void done(Object o) {
        if (o != null)
          sendJSON(Pipes.tapFromString(Ad.marshal(o)));
      } public void fail(Throwable t) {
        // If it's a special redirect error, send a redirect.
        if (t instanceof Redirect) {
          Redirect redirect = (Redirect) t;
          body.location = redirect.url;
          body.status = FOUND;
          sendJSON(Pipes.tapFromString(errorToAd(t)));
        } else {
          body.status = INTERNAL_SERVER_ERROR;
          sendJSON(Pipes.tapFromString(errorToAd(t)));
        }
      } private void sendJSON(Tap tap) {
        body.contentType = "application/json; charset=UTF-8";
        tap.attach(body.sink()).tap().start();
      }
    };
  }

  // Convert an HTTP request to an ad asynchronously.
  private Bell<Request> fillRequestForm(final HTTPRequest hr) {
    Request request = getRequestForm(hr.uri.path().name());

    // This should be the only instance of the root resource.
    request.resource = hr.root();

    if (hr.method().equals(HttpMethod.POST))
      request.mayChangeState = true;
    else
      request.mayChangeState = false;
    if (hr.cookie() != null)
      request.cookie(cookiesToMap(hr.cookie()));
    if (hr.uri.query() != null)
      request.unmarshalFrom(queryToAd(hr.uri.query()));
    if (!hr.hasBody())
      return new Bell<Request>(request);
    else
      return handleRequestBody(hr, request);
  }

  // Asynchronously handle an HTTP request body.
  private Bell<Request> handleRequestBody(HTTPRequest hr, final Request req) {
    String type = hr.type();
    Pipes.AggregatorSink sink = Pipes.aggregatorSink();
    Bell<Ad> bell;

    // Make sure it's a type we can handle.
    if (type == null || type.startsWith("application/json")) {
      bell = sink.bell().new As<Ad>() {
        public Ad convert(Slice slice) {
          return Ad.parse(new ByteBufInputStream(slice.asByteBuf()));
        }
      };
    } else if (type.startsWith("application/x-www-form-urlencoded")) {
      bell = sink.bell().new As<Ad>() {
        public Ad convert(Slice slice) {
          return queryToAd(slice.asByteBuf().toString(CharsetUtil.UTF_8));
        }
      };
    } else {
      req.ring(new Exception("Unsupported content type."));
      return new Bell<Request>(req);
    }

    hr.root().tap().attach(sink).tap().start();

    return bell.new As<Request>() {
      public Request convert(Ad body) { return req.unmarshalFrom(body); }
    };
  }

  // Convert a cookie string into an ad.
  private Map<String,String> cookiesToMap(String cookie) {
    Map<String,String> map = new HashMap<String,String>();
    Set<Cookie> cookies = CookieDecoder.decode(cookie);
    for (Cookie c : cookies)
      map.put(c.getName(), c.getValue());
    return map;
  }

  // Convert a query string into an ad.
  private Ad queryToAd(String query) {
    Ad ad = new Ad();
    QueryStringDecoder qsd = new QueryStringDecoder(query, false);
    Map<String, List<String>> map = qsd.parameters();

    // XXX: This is a really terrible hack, but we need it since the web
    // interface relies on it for some very specific things.
    if (map.size() == 1 && map.containsKey("$json")) {
      ad = Ad.parse(map.get("$json").get(0));
      if (ad == null)
        throw new RuntimeException("Bad $json field.");
      return ad;
    }

    for (String k : map.keySet())
      ad.put(k, map.get(k).get(0));
    return ad;
  }

  // A codec for converting ads to HTTP responses.
  private class HTTPAdEncoder extends MessageToMessageEncoder<Ad> {
    public void encode(ChannelHandlerContext ctx, Ad ad, List<Object> out) {
      String error = ad.get("error");
      FullHttpResponse r;
      if (error == null) {
        ByteBuf b = Unpooled.copiedBuffer(ad.toJSON().getBytes());
        r = new DefaultFullHttpResponse(HTTP_1_1, OK, b);
        r.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
      } else {
        ByteBuf b = Unpooled.copiedBuffer(error.getBytes());
        r = new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR, b);
        r.headers().set(CONTENT_TYPE, "text/plain");
      } out.add(r);
    }
  }
}
