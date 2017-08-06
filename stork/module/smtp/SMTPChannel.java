package stork.module.smtp;

import stork.feather.*;
import io.netty.bootstrap.*;
import io.netty.channel.*;
import io.netty.channel.nio.*;
import io.netty.buffer.*;
import io.netty.channel.socket.nio.*;
import io.netty.channel.socket.*;
import io.netty.handler.codec.*;
import io.netty.handler.codec.base64.*;
import io.netty.util.*;

import java.util.*;

public class SMTPChannel {
  LinkedList<Bell<String>> replies = new LinkedList<Bell<String>>();
  Bell bell;
  EventLoopGroup workerGroup = new NioEventLoopGroup();
  ChannelFuture channelFuture;

  SMTPChannel(final Bell bell) {
    Bootstrap b = new Bootstrap();
    b.group(workerGroup);
    b.channel(NioSocketChannel.class);
    b.option(ChannelOption.SO_KEEPALIVE, true);
    b.handler(new ChannelInitializer<SocketChannel>() {
      public void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(new LineBasedFrameDecoder(1000) {
          public void channelRead(ChannelHandlerContext ctx, Object msg) {
            String str = ((ByteBuf)msg).toString(CharsetUtil.UTF_8);
            if (!replies.isEmpty())
              replies.pop().ring(str);
          }
        });
        bell.ring();
      }
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        bell.ring(cause);
      }
    });
    //channelFuture = b.connect("smtp.buffalo.edu",25);
    channelFuture = b.connect("localhost",12345);
    this.bell = bell;
  }

  public void enableBase64() {
    Bell last = replies.peekLast();
    if (last == null)
      last = Bell.rungBell();
    last.new Promise() {
      public void done() {
        channelFuture.channel().pipeline().addLast("Base64Encoder", new Base64Encoder());
      }
    };
  }

  public void disableBase64() {
    Bell last = replies.peekLast();
    if (last == null)
      last = Bell.rungBell();
    last.new Promise() {
      public void done() {
        channelFuture.channel().pipeline().remove("Base64Encoder");
      }
    };
  }

  public synchronized Bell<String> sendCommand(final String command) {
    Bell<String> bell = new Bell<String>();
    Bell last = replies.peekLast();

    replies.add(bell);

    if (last == null)
      last = Bell.rungBell();

    last.new Promise() {
      public void done() {
        byte[] bytes = (command+"\r\n").getBytes();
        channelFuture.channel().writeAndFlush(Unpooled.wrappedBuffer(bytes));
      }
    };

    return bell;
  }

  public synchronized Bell send(final String line) {
    Bell last = replies.peekLast();

    if (last == null)
      last = Bell.rungBell();

    return last.new Promise() {
      public void done() {
        byte[] bytes = (line+"\r\n").getBytes();
        channelFuture.channel().writeAndFlush(Unpooled.wrappedBuffer(bytes));
      }
    };
  }

  public synchronized Bell send(final ByteBuf bytebuf) {
    Bell last = replies.peekLast();

    if (last == null)
      last = Bell.rungBell();

    return last.new Promise() {
      public void done() {
        channelFuture.channel().writeAndFlush(bytebuf);
      }
    };
  }

  public Bell getBell() {
    return bell;
  }

  public void close() {
    channelFuture.channel().close();
  }
}
