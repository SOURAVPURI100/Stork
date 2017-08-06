package stork.core.net;

import java.net.*;

import io.netty.bootstrap.*;
import io.netty.buffer.*;
import io.netty.handler.codec.*;
import io.netty.channel.*;
import io.netty.channel.socket.*;
import io.netty.channel.nio.*;
import io.netty.channel.socket.nio.*;

import stork.ad.*;
import stork.core.server.*;
import stork.feather.*;
import stork.feather.URI;

/**
 * A base implementation of {@code StorkInterface} which is based on a TCP
 * socket.
 */
public abstract class BaseTCPInterface extends StorkInterface {
  protected final URI uri;
  private final SocketAddress address;
  private static final NioEventLoopGroup acceptor = new NioEventLoopGroup();

  /**
   * Create a {@code BaseTCPInterface} for the given server and URI.
   *
   * @param server the server to provide an interface for.
   * @param uri a URI describing the listening connection.
   */
  public BaseTCPInterface(Server server, URI uri) {
    super(server);
    this.uri = uri;
    address = initChannel();
  }

  // Initialize the channel and return the address used to bind the channel.
  private SocketAddress initChannel() {
    ServerBootstrap sb = new ServerBootstrap();
    sb.channel(NioServerSocketChannel.class);
    sb.group(acceptor);
    sb.childHandler(new ChannelInitializer<SocketChannel>() {
      protected void initChannel(SocketChannel ch) {
        BaseTCPInterface.this.init(ch);
      }
    });

    // Set some nice options everyone likes.
    sb.option(ChannelOption.TCP_NODELAY, true);
    sb.option(ChannelOption.SO_KEEPALIVE, true);

    // Determine host and port from uri.
    InetAddress ia;
    try {
      ia = InetAddress.getByName(uri.host());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    int p = uri.port();
    if (p <= 0) p = port(uri);
    InetSocketAddress addr = new InetSocketAddress(ia, p);

    // Bind socket to the given host/port.
    sb.bind(addr);

    return addr;
  }

  /**
   * Return a string representation of the listening address.
   *
   * @return A string representation of the listening address.
   */
  public String address() {
    return address.toString();
  }

  /**
   * Determine the port from a URI without an explicit port number.
   *
   * @param uri the URI to determine the port from.
   * @return The port based on {@code uri}.
   */
  public abstract int port(URI uri);

  /**
   * Initialize the {@code SocketChannel} created for this interface. This
   * initialization method should attach handlers which eventually call {@link
   * StorkInterface#issueRequest(Request)} and attach callbacks to the request
   * to write the response back to the requestor.
   *
   * @param channel the {@code SocketChannel} to initialize.
   */
  protected abstract void init(SocketChannel channel);
}
