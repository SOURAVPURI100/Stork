package stork.module.http;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.concurrent.GenericFutureListener;
import stork.feather.Bell;
import stork.feather.Path;
import stork.feather.URI;
import stork.module.http.HTTPResource.HTTPTap;

/**
 * Sets up {@link HTTPChannel} for a {@link HTTPSession}. It also maintains
 * multiple states for the session.
 * <p>
 * {@code Test state}: Collects connection information before initializing
 * the connection. Represented by a {@link Bell}, which rings after the 
 * test phase is complete.
 * <p>
 * {@code Connect state}: Be able to start downloading {@link HTTPResource}
 * requests. Represented by a {@link Bell}, which rings when the test
 * phase is passed, and the connection has been fully established.
 * <p>
 * {@code Close state}: Rings when this {@link HTTPSession} is asked to get
 * closed.
 * <p>
 * {@code Keep-alive state}: Tells whether the connection can always be
 * open. If this session is able to keep alive, its {@link HTTPChannel}
 * would be kept open until a {@code close} method is called. Otherwise,
 * its {@link HTTPChannel} would be reset for each new {@link HTTPTap} task
 * extracted from the queue waited in this local {@code HTTPBuilder}.
 */
public class HTTPBuilder {
  // Bell used to indicate the close state of session
  protected final Bell<Void> onCloseBell = new Bell<Void> ();
  // Bell rung when channel passes the connection test before using
  protected Bell<Void> onTestBell;
  // Bell rung when the channel is ready to use
  protected Bell<Void> onConnectBell;
  // Queue that stores all unhandled requested taps
  protected Queue<Bell<Void>> tapBellQueue;
  // Tells the connection state, set by final connection test result 
  volatile protected boolean isKeepAlive = true;

  public HTTPChannel channel;
  private Bootstrap boot;
  private URI uri;
  private int port;

  /** Constructor that sets up the connection */
  public HTTPBuilder(HTTPSession session) {
    try {
      boot = new Bootstrap();
      boot.group(session.workGroup)
        .channel(HTTPChannel.class)
        .handler(new HTTPInitializer(session.uri.scheme(), this));

      // Channel setup
      onConnectBell = new Bell<Void>();
      setUri(session.uri);
      setupWithTest();

      // Tap bells queue setup
      tapBellQueue = new ConcurrentLinkedQueue<Bell<Void>>();
    } catch (HTTPException e) {
      System.err.println(e.getMessage());
    }
  }

  /** 
   * Closes and clears up {@code HTTPChannel} for this session.
   * It is completely closed when all unfinished connection is done.
   */
  public void close() {
    if (!onCloseBell.isDone()) {
      onCloseBell.ring();
      channel.clear();
    }
  }

  /**
   * Tells if the connection supports {@code keep-alive} option 
   * 
   * @return current connection support on {@code keep-alive}
   * option
   */
  public boolean isKeepAlive() {
    return isKeepAlive;
  }

  /**
   * Sets during the {@code Test state} 
   * 
   * @param v {@code true} for supporting {@code keep-alive}
   * option. Otherwise, uses {@code false};
   */
  protected void setKeepAlive(boolean v) {
    isKeepAlive = v;
  }

  /**
   * Establishes a new socket connection with connection test
   */
  protected void setupWithTest() {
    ChannelFuture future = boot.connect(uri.host(), port);
    future.addListener(
        new GenericFutureListener<ChannelFuture>() {

          public void operationComplete(ChannelFuture f) {
            if (f.isSuccess()) {
              channel = (HTTPChannel) f.channel();
              testConnection();
              onTestBell.promise(onConnectBell);
            } else {
              onConnectBell.ring(f.cause());
            }
          }
        });
  }

  /**
   * Recreates a new socket connection in case that 
   * {@code keep-alive} option is not available.
   * 
   * @param tap the tap that the connection resets for
   */
  protected void tryResetConnection(HTTPTap tap) {
    // Bell rung when the channel has been established
    final HTTPTap localTap = tap;
    final Bell<HTTPChannel> connectBell =
      new Bell<HTTPChannel>() {

        protected void done() throws InterruptedException {
          try {
            HTTPChannel channel = (HTTPChannel) this.get();
            HTTPBuilder.this.channel = channel;
            channel.addChannelTask(localTap);
            channel.writeAndFlush(prepareGet(localTap.getPath().toString()));
          } catch (ExecutionException e) {
            System.err.println(e.getMessage());
            HTTPBuilder.this.channel.clear();
            HTTPBuilder.this.channel.close();
          }
        }
      };

    // Case 1. Starts a new connection immediately if the channel is 
    // in idle status
    synchronized (channel) {
      if (channel.onInactiveBell.isDone() &&
          tapBellQueue.isEmpty()) {
        // Starts reconnecting
        ChannelFuture f = boot.connect(uri.host(), port);
        f.addListener(
            new GenericFutureListener<ChannelFuture>() {

              public void operationComplete(ChannelFuture f) {
                if (f.isSuccess()) {
                  connectBell.ring((HTTPChannel) f.channel());
                } else {
                  connectBell.ring(f.cause());
                }
              }
            });
      } else {
        // Case 2. Otherwise, adds the resource request to 
        // waiting queue. Bell rung when the channel finishes
        // all its previous tasks in the queue.
        Bell<Void> waitBell = new Bell<Void>();
        Bell<Void> createBell = new Bell<Void>() {

          protected void done() {
            ChannelFuture f = boot.connect(uri.host(), port);
            f.addListener(
              new GenericFutureListener<ChannelFuture>() {

                public void operationComplete(ChannelFuture f) {
                  if (f.isSuccess()) {
                    connectBell.ring((HTTPChannel) f.channel());
                  } else {
                    connectBell.ring(f.cause());
                  }
                }
              }
            );
          }
        };

        waitBell.promise(createBell);
        tapBellQueue.offer(waitBell);
      }
    }
  }

  /** 
   * Modifies the host {@code URI} for a valid connection of this session.
   * 
   * @param uri new host name {@link URI}
   * */
  protected void setUri(URI uri) {
    String strUri = uri.toString();
    if (strUri.endsWith("/")) {
      strUri = strUri.substring(0, strUri.length() - 1);
    }
    this.uri = URI.create(strUri);
    try {
      port = analURI(uri);
    } catch (HTTPException e) {
      System.err.println(e.getMessage());
    }
  }

  /** 
   * Gets modified host name 
   * 
   * @return host name in string
   */
  protected String getHost() {
    return uri.host();
  }

  /**
   * Gets available {@code HTTPChannel} instance.
   * 
   * @return A HTTP channel
   */
  protected HTTPChannel getChannel() {
    return channel;
  }

  /**
   * Prepares GET request message to be sent.
   * 
   * @param path specific file path under this host
   * @return Message to be sent
   */
  protected HttpRequest prepareGet(String path) {
    return prepareRequest("GET", path);
  }

  /**
   * Prepares HEAD request message to be sent.
   * 
   * @param path specific file path under this host
   * @return Message to be sent
   */
  protected HttpRequest prepareHead(String path) {
    return prepareRequest("HEAD", path);
  }

  /**
   * Prepares request message to be sent.
   * 
   * @param path specific file path under this host
   * @return Message to be sent
   */
  protected HttpRequest prepareRequest(String method, String path) {
    HttpRequest request = new DefaultFullHttpRequest( 
        HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), path);
    request.headers().set(HttpHeaders.Names.HOST, this.uri.host());
    request.headers().set(HttpHeaders.Names.USER_AGENT, "Stork");

    return request;
  }

  // Tests whether the connection supports keep-alive.
  private void testConnection() {
    HttpRequest request = prepareGet(uri.path().toString());
    onTestBell = new Bell<Void>() {
      protected void done() {
        channel.config().setKeepAlive(isKeepAlive);
        if (isKeepAlive) {
          channel.restorePipeline(HTTPBuilder.this);
        }
      }
    };
    request.setMethod(channel.testMethod);
    channel.testerPipeline(this);
    channel.config().setKeepAlive(true);
    channel.writeAndFlush(request);
  }

  // Returns an appropriate port number from given URL.
  private int analURI(URI uri) throws HTTPException {
    int port = -1;

    if (uri.host() == null) {
      throw new HTTPException("Error: null host name");
    }

    if (uri.port() == -1) {
      if (uri.scheme().equalsIgnoreCase("http")) {
        port = 80;
      } else if (uri.scheme().equalsIgnoreCase("https")) {
        port = 443;
      }
    } else {
      port = uri.port();
    }

    if (port == -1) {
      throw new HTTPException("Error: incorrect port number");
    }

    return port;
  }
}
