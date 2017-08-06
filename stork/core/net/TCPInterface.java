package stork.core.net;

import java.net.*;
import java.util.*;
import java.io.*;

import io.netty.buffer.*;
import io.netty.handler.codec.*;
import io.netty.channel.*;
import io.netty.channel.socket.*;

import stork.ad.*;
import stork.core.server.*;
import stork.feather.URI;

/**
 * Basic TCP interface.
 */
public class TCPInterface extends BaseTCPInterface {
  private URI uri;

  public TCPInterface(Server s, URI uri) {
    super(s, uri);
    this.uri = uri;
  }

  public String address() { return uri.host()+":"+port(uri); }

  public String name() { return "TCP"; }

  public void init(SocketChannel channel) {
    channel.pipeline().addLast(new AdDecoder());
    channel.pipeline().addLast(new SimpleChannelInboundHandler<Ad>() {
      public void messageReceived(final ChannelHandlerContext ctx, Ad ad) {
        Request r = getRequestForm(ad.get("command")).unmarshalFrom(ad);
        r.mayChangeState = true;  // Always allow state change.
        issueRequest(r).new Promise() {
          public void done(Object res) {
            ctx.channel().writeAndFlush(Ad.marshal(res));
          } public void fail(Throwable t) {
            ctx.channel().writeAndFlush(errorToAd(t));
          }
        };
      }
    });
    channel.pipeline().addLast(new AdEncoder());
  }

  public int port(URI uri) {
    return uri.port() > 0 ? uri.port() : 57024;
  }
}

class AdDecoder extends ReplayingDecoder<Void> {
  protected void decode(
      ChannelHandlerContext ctx, final ByteBuf buf, List<Object> out) {
    // A decoder for reading serialized ads from a byte channel.
    // TODO: We should use a smarter method of finding message ends.
    if (actualReadableBytes() == 0)
      return;

    Ad ad = Ad.parse(new InputStream() {
      int i = 0, len = actualReadableBytes();
      public int read() {
        return (i++ == len) ? -1 : buf.readByte();
      }
    });
    out.add(ad);
  }

  public void exceptionCaught(ChannelHandlerContext ctx, Throwable t) {
    // We got a parse error. This may lead to desynchronization, but we should
    // leave closing of the connection up to another handler.
    t.printStackTrace();
  }
}

class AdEncoder extends MessageToMessageEncoder<Ad> {
  public void encode(ChannelHandlerContext ctx, Ad ad, List<Object> out) {
    out.add(Unpooled.wrappedBuffer(ad.toString().getBytes()));
  }
}
