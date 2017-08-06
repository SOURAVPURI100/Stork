package stork.module.http;

import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import stork.feather.Bell;
import stork.module.http.HTTPResource.HTTPTap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.timeout.ReadTimeoutHandler;

/**
 * Controls data transmissions on this connection.
 */
public class HTTPChannel extends NioSocketChannel {

  // Queue of resource taps 
  protected Queue<HTTPTap> tapQueue = new ConcurrentLinkedQueue<HTTPTap>();
  // Bell rung when the channel goes to inactive state
  protected Bell<Void> onInactiveBell = new Bell<Void>();
  protected HttpMethod testMethod = HttpMethod.HEAD;
  // Current tap that is being considered
  protected HTTPTap tap;
  private boolean readable = true;

  /* Constructors */
  public HTTPChannel(Channel parent, EventLoop eventLoop, SocketChannel socket) {
    super(parent, eventLoop, socket);
  }
  public HTTPChannel(EventLoop eventLoop, SocketChannel socket) {
    super(eventLoop, socket);
  }
  public HTTPChannel(EventLoop eventLoop) {
    super(eventLoop);
  }

  /**
   * Adds a new task to the waiting queue, which would be processed in future.
   * 
   * @param tap A {@link HTTPTap} transmission request
   */
  protected void addChannelTask(HTTPTap tap) {
    tapQueue.offer(tap);
  }

  /**
   * Stops/starts receiving data.
   * 
   * @param readable {@code true} for enabling to receive data;
   * {@code false} for the opposite.
   */
  protected void setReadable(boolean readable) {
    this.readable = readable;
  }

  /** Clears all fields stored on this channel. */
  protected void clear() {
    for (HTTPTap tap : tapQueue) {
      tap.onStartBell.cancel();
    }
    tapQueue.clear();
  }

  protected int doReadBytes(ByteBuf buf) throws Exception {
    if (readable) {
      return buf.writeBytes(javaChannel(), buf.writableBytes());
    } else {
      return 0;
    }
  }

  /**
   * Installs test handler at the initial run of this {@code HTTPSession}.
   * 
   * @param builder {@link HTTPBuilder} for this {@link HTTPSession}
   */
  protected void testerPipeline(HTTPBuilder builder) {
    pipeline().remove("Timer");
    pipeline().remove("Handler");
    pipeline().addLast("Tester", new HTTPTestHandler(builder));
  }

  /**
   * Installs message receiver handler back after {@code test} state.
   * 
   * @param builder {@link HTTPBuilder} for this {@link HTTPSession}
   */
  protected void restorePipeline(HTTPBuilder builder) {
    pipeline().remove("Tester");
    pipeline().addFirst("Timer", new ReadTimeoutHandler(30));
    pipeline().addLast("Handler", new HTTPMessageHandler(builder));
  }
}
