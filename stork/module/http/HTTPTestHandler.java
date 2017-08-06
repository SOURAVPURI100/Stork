package stork.module.http;

import stork.feather.URI;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * Actions to take according to response code status.
 */
enum ActionCode {
  OK,
  Redirect,
  NotFound,
  Bad
}

/**
 * Handles downstream test connection.
 */
public class HTTPTestHandler extends ChannelHandlerAdapter {

  private HTTPBuilder builder;
  private ActionCode code;

  /**
   * Constructs a channel for testing this HTTP connection. Specifically,
   * the support on {@code keep-alive} option, existence of its host, and
   * correctness of domain name would be tested.
   * 
   * @param utility {@link HTTPBuilder} that initiates this test channel
   */
  public HTTPTestHandler(HTTPBuilder utility) {
    this.builder = utility;
  }

  /**
   * Reads the header part of response from remote HTTP server. Tests
   * the validity of this connection.
   * 
   * @param ctx handler context of this channel
   * @param msg received message
   */
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (msg instanceof HttpResponse) {
      HttpResponse response = (HttpResponse) msg;
      HttpResponseStatus status = response.getStatus();
      try {
        if (HTTPResponseCode.isMoved(status)) {
          URI uri = URI.create(
              response.headers().get(HttpHeaders.Names.LOCATION));
          builder.setUri(URI.create(uri.endpoint()));
          code = ActionCode.Redirect;
          throw new HTTPException(builder.getHost() + " " + status.toString());
        } else if (HTTPResponseCode.isNotFound(status)) {
          code = ActionCode.NotFound;
          throw new HTTPException(builder.getHost() + " " + status.toString());
        } else if (HTTPResponseCode.isInvalid(status)) {
          code = ActionCode.Bad;
          throw new HTTPException(
              builder.getHost() + " HEADER method unsupported");
        } else if (HTTPResponseCode.isOK(status)) try {
          // Valid HTTP server found
          code = ActionCode.OK;
          builder.setKeepAlive(!response.headers()
              .get("Connection").equalsIgnoreCase("close"));
        } catch (NullPointerException npe) {
          // No connection header.
          builder.setKeepAlive(true);
        }
      } catch (HTTPException e) {
        System.err.println(e.getMessage());
      }
    }
    if (msg instanceof HttpContent) {
      endTest(ctx);
    }
  }

  // Decides the action to take after receiving contents at the 
  // test phase.
  private void endTest(ChannelHandlerContext ctx) {
    final HTTPChannel ch = (HTTPChannel) ctx.channel();

    if (builder.isKeepAlive && (code == ActionCode.OK)) {
      builder.onTestBell.ring();
    } else {
      ch.disconnect();
      ch.close().addListener(new GenericFutureListener<ChannelFuture> () {

        public void operationComplete(ChannelFuture future) {

          switch(code.ordinal()) {
            case 3:
              if (ch.testMethod.equals(HttpMethod.HEAD)) {
                ch.testMethod = HttpMethod.GET;
              } else {
                System.err.println("Error: Bad request on " + 
                    builder.getHost() +
                    ". The session will be closed.");
                builder.close();
                ch.clear();
                ch.close();
                break;
              }
            case 1:
              builder.setupWithTest();
            case 2:
              break;
            case 0: 
              ch.onInactiveBell.ring();
              builder.onTestBell.promise(builder.onConnectBell);
              builder.onTestBell.ring();
              break;
            default:
              System.err.println(
                  "Error: Unimplemented response code on " +
                  builder.getHost() + ". The session will be closed.");
              builder.close();
              ch.close();
              break;
          }
        }
      });
    }
  }
}
