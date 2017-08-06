package stork.module.http;

import java.util.concurrent.*;

import io.netty.channel.*;
import io.netty.channel.nio.*;
import io.netty.util.concurrent.*;

import stork.feather.*;
import stork.feather.util.*;

/**
 * A HTTP download session
 */
public class HTTPSession extends Session<HTTPSession, HTTPResource> {

  protected EventLoopGroup workGroup;
  protected HTTPBuilder builder;

  /**
   * A constructor of {@code HTTPSession} with a domain described
   * by {@link URI}.
   * 
   * @param uri A URL with host name
   */
  public HTTPSession(URI uri) {
    super(uri);
    workGroup = new NioEventLoopGroup();
  }

  public HTTPResource select(Path path) {
    HTTPResource resource = new HTTPResource(this, path);

    return resource;
  }

  public Bell<HTTPSession> initialize() {
    return new Bell<Object> () {{
      // Initialize the connection
      builder = new HTTPBuilder(HTTPSession.this);
      builder.onConnectBell.promise(this);

      // Set up the session close reaction
      builder.onCloseBell.new Promise() {

        public void always() {builder.tapBellQueue.clear();}
      };
    }}.as(this);
  }

  public void cleanup() {
    try {
      builder.close();
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
  }
}
