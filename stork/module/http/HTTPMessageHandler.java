package stork.module.http;

import java.text.*;
import java.util.*;

import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.*;
import io.netty.util.concurrent.*;

import stork.feather.*;
import stork.feather.errors.*;
import stork.module.http.HTTPResource.HTTPTap;

/**
 * Message handler receiving status.
 */
enum Status {
  Header,
  Content,
  NotFound,
  Closed
}

/**
 * Handles client-side downstream.
 */
public class HTTPMessageHandler extends ChannelHandlerAdapter {

  private HTTPBuilder builder;
  private HTTPTap tap;
  private Status status = Status.Header;

  /**
   * Constructs a data receiver channel
   * 
   * @param util {@link HTTPBuilder} that initiates this channel
   */
  public HTTPMessageHandler(HTTPBuilder  util) {
    this.builder = util;
  }

  /**
   * Receives meta data and data from remote HTTP server.
   * 
   * @param ctx handler context of this channel
   * @param msg received content
   */
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    final HTTPChannel ch = (HTTPChannel) ctx.channel();

    if (msg instanceof HttpResponse) {
      HttpResponse resp = (HttpResponse) msg;
      String connection = resp.headers().get(HttpHeaders.Names.CONNECTION);

      if (status == Status.Header) {
        // This is the first packet of response, needs to know the 
        // requesting resource of it.
        status = Status.Content;
        tap = ch.tapQueue.poll();
      }

      // Directly closes local end if remote
      // server does not comprehend the sent close request
      if (status == Status.Closed) {
        if (connection == null) {
          ch.close();
          return;
        } else if (connection.equals(HttpHeaders.Values.KEEP_ALIVE)) {
          ch.close();
          return;
        }
      }

      // Normally, this shouldn't happen. It is assumed that
      // a HTTP server always remains in the same connection state.
      if (connection != null) {
        if (connection.equals(HttpHeaders.Values.CLOSE) &&
            builder.isKeepAlive()) {
          for (HTTPTap tap: ch.tapQueue) {
            builder.tryResetConnection(tap);
          }
          builder.setKeepAlive(false);
        }
      }

      caseHandler(resp, ch);

      if (status == Status.Content) {
        if (!tap.hasStat()) {
          // The resource this tap belongs to has not 
          // received meta data yet. Do it now.
          tap.setStat(getStat(resp));
        }
      }

      // Only pause if there's a sink to wait for.
      if (tap.sinkReadyBell != null) {
        ch.setReadable(false);
        tap.sinkReadyBell.new Promise() {
          public void done() { ch.setReadable(true); }
        };
      }
    }
    else if (msg instanceof HttpContent) {
      HttpContent content = (HttpContent) msg;

      if (status == Status.Content) {
        ByteBuf buf = content.content();
        Slice slice = new Slice(buf);

        // Ring the bell once the received data is ready
        tap.onStartBell.ring();

        tap.drain(slice);
      }

      if (content instanceof LastHttpContent) {
        if (status == Status.Content) {
          tap.finish(null);
        }
        if (builder.isKeepAlive()) {
          if (builder.onCloseBell.isDone()) {
            // Creates a close request to
            // remote server if session is closed
            if (status != Status.Closed) {
              notifyClose(ch);
            }
          } else {
            status = Status.Header; // Reset status.
          }
        }
      }
    }
  }

  /**
   * Called whenever this current channel is in inactive state.
   * It usually happens after finishing the data receiving from server.
   * 
   * @param ctx handler context of this channel
   */
  public void channelInactive(ChannelHandlerContext ctx) {
    final HTTPChannel ch = (HTTPChannel) ctx.channel();

    ch.close().addListener(new GenericFutureListener<ChannelFuture>() {

      public void operationComplete(ChannelFuture arg0) throws Exception {
        if (!builder.isKeepAlive()) {
          if (builder.onCloseBell.isDone()) {
            ch.clear();
          } else {
            synchronized(ch) {
              Bell<Void> bell = builder.tapBellQueue.poll();
              if (bell == null) {
                ch.onInactiveBell.ring();
              } else {
                ch.onInactiveBell.promise(bell);
                ch.onInactiveBell.ring();
              }
            }
          }
        } else {
          // close this channel resource
          ch.onInactiveBell.ring();
          ch.clear();
          ch.close();
        }
      }
    });
  }

  /**
   * Called when an exception is caught, usually by a read time-out from
   * remote server.
   * 
   * @param ctx handler context of this channel
   * @param cause {@link Throwable} that tells the exception
   */
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    // Close the connection when an exception is raised.
    tap.finish(cause);
    if (cause instanceof ReadTimeoutException) {
      ctx.fireChannelInactive();
    } else {
      cause.printStackTrace();
      ctx.close();
    }
  }

  // Handles various abnormal response codes.
  private void caseHandler(HttpResponse response, HTTPChannel channel) {
    HttpResponseStatus status = response.getStatus();
    if (HTTPResponseCode.isMoved(status)) {
      this.status = Status.NotFound;
      // Redirect to new location
      String newLocation =
        response.headers().get(HttpHeaders.Names.LOCATION);
      String suffix = newLocation.endsWith("/") ? "/" : "";
      URI uri = URI.create(newLocation);
      tap.setPath(uri.path()+suffix);
      if (builder.isKeepAlive()) {
        channel.addChannelTask(tap);
        channel.writeAndFlush(builder.prepareGet(tap.getPath().toString()));
      } else {
        builder.tryResetConnection(tap);
      }
    } else if (HTTPResponseCode.isNotFound(status)) {
      throw new NotFound();
    }
  }

  // Gathers meta data information
  private Stat getStat(HttpResponse response) {
    Stat stat = new Stat(tap.getPath().toString());
    String length = response.headers().get("Content-Length");;
    String type = response.headers().get("Content-Type");
    Date time = null;

    try {
      time = HttpHeaders.getDate(response);
    } catch (ParseException e) {
      // This means date meta data is not available
    }
    stat.dir = (type != null && type.startsWith("text/html"));
    stat.file = !stat.dir;
    stat.link = tap.getPath().toString();
    stat.size = (length == null) ? -1l : Long.valueOf(length);
    stat.time = (time == null) ? -1l : time.getTime()/1000;

    return stat;
  }

  // Notifies remote HTTP server to close connection
  private void notifyClose(HTTPChannel ch) {
    ch.clear();
    HttpRequest request =
      builder.prepareGet(tap.getPath());
    HttpHeaders.setKeepAlive(request, false);
    ch.writeAndFlush(request);
    status = Status.Closed;
  }
}
