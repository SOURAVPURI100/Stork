package stork.feather.net;

import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import stork.feather.*;

import static java.net.StandardSocketOptions.*;

/**
 *
 */
class TCPSocket extends Socket {
  private Bell<SocketAddress> addr;
  private Bell<SocketChannel> channel;
  private Bell<ServerSocketChannel> server;

  /** Our selectors. */
  private Selector<SocketChannel> selector;
  private Selector<ServerSocketChannel> acceptor;

  private ByteBuffer readBuffer;
  private List<ByteBuffer> writeBuffer = new LinkedList<ByteBuffer>();

  public TCPSocket() {
    this(0);
  }

  public TCPSocket(int port) {
    this(DNSResolver.resolve(null), port);
  }

  public TCPSocket(String host) {
    this(DNSResolver.resolve(host), 0);
  }

  public TCPSocket(String host, int port) {
    this(DNSResolver.resolve(host), port);
  }

  public TCPSocket(InetAddress host, int port) {
    this(new InetSocketAddress(host, port));
  }

  public TCPSocket(Bell<InetAddress> host, final int port) {
    addr = host.new As<SocketAddress>() {
      public SocketAddress convert(InetAddress addr) {
        return new InetSocketAddress(addr, port);
      }
    };
  }

  public TCPSocket(SocketAddress addr) {
    this.addr = Bell.wrap(addr);
  }

  public TCPSocket(Bell<SocketAddress> addr) {
    this.addr = addr;
  }

  /** An already connected socket. */
  private TCPSocket(SocketChannel ch) {
    channel = Bell.wrap(ch);
    setConnectedSocket(ch);
    connect();
  }

  protected Bell<?> doConnect() {
    if (channel != null)
      return channel;
    return channel = addr.new As<SocketChannel>() {
      public SocketChannel convert(SocketAddress addr) throws Exception {
        return makeSocket(addr);
      }
    };
  }

  /** Call this to set up a new unconnected socket. */
  private SocketChannel makeSocket(SocketAddress addr) throws Exception {
    SocketChannel ch = SocketChannel.open();
    configureSocket(ch);
    selector = new Selector<SocketChannel>(ch);
    selector.onConnectable().new As<SocketChannel>() {
      public SocketChannel convert(SocketChannel ch) throws Exception {
        ch.finishConnect();
        return ch;
      } public void done(SocketChannel ch) {
        setConnectedSocket(ch);
      } public void fail(Throwable t) {
        close(t);
      }
    };
    ch.connect(addr);
    return ch;
  }

  /** Configure a socket. */
  private void configureSocket(SocketChannel ch) throws Exception {
    ch.setOption(TCP_NODELAY, true);
    ch.setOption(SO_KEEPALIVE, true);
  }

  /** Call this to set up state when a socket is connected. */
  private synchronized void setConnectedSocket(SocketChannel ch) {
    if (selector == null)
      selector = new Selector<SocketChannel>(ch);
    allocateReadBuffer();
    expectRead();
  }

  protected Bell<?> doListen() {
    if (server != null)
      return server;
    server = new Bell<ServerSocketChannel>();
    return addr.new As<ServerSocketChannel>() {
      public ServerSocketChannel convert(SocketAddress addr)
      throws Exception {
        ServerSocketChannel ch = ServerSocketChannel.open();
        ch.setOption(SO_REUSEADDR, true);
        acceptor = new Selector<ServerSocketChannel>(ch);
        ch.bind(addr);
        expectAccept();
        return ch;
      }
    }.promise(server);
  }

  private synchronized void expectAccept() {
    acceptor.onAcceptable().new As<Socket>() {
      public Socket convert(ServerSocketChannel ssc) throws Exception {
        SocketChannel sc = ssc.accept();
        if (sc == null)
          throw new RuntimeException();
        sc.configureBlocking(false);
        return new TCPSocket(sc);
      } public void done(Socket socket) {
        try {
          accept(socket);
        } catch (Exception e) {
          socket.close(e);
        } finally {
          expectAccept();
        }
      } public void always() {
        expectAccept();
      }
    };
  }

  /** Allocate a new read buffer. */
  private synchronized void allocateReadBuffer() {
    readBuffer = ByteBuffer.allocate(4096);
  }

  // Call when we're expecting to read.
  private void expectRead() {
    selector.onReadable().new Promise() {
      public void done(SocketChannel ch) { doRead(ch); }
    };
  }

  // Read into the readBuffer.
  private synchronized void doRead(SocketChannel ch) {
    int size;
    byte[] bytes;

    try {
      size = ch.read(readBuffer);
    } catch (Exception e) {
      close(e);
      return;
    }

    // There was nothing to read after all...
    if (size == 0) {
      expectRead();
      return;
    }

    // If we used the whole read buffer, just emit it.
    if (!readBuffer.hasRemaining()) {
      bytes = readBuffer.array();
      allocateReadBuffer();
    } else {
      bytes = new byte[size];
      readBuffer.position(0);
      readBuffer.get(bytes);
      readBuffer.position(0);
    }

    // Emit and don't try to read again until the pipeline is ready.
    emit(bytes).new Promise() {
      public void done() { expectRead(); }
    };
  }

  private Bell<Void> lastWrite = Bell.rungBell();

  protected synchronized void code(byte[] bytes) {
    if (bytes.length == 0)
      return;
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    lastWrite = lastWrite.new AsBell<Void>() {
      public Bell<Void> convert(Void _) { return write(buffer); }
    }.detach();
  }

  protected void code(Throwable error) {
    close(error);
  }

  /**
   * Write to the channel. Returns a bell that rings when all of the bytes have
   * been written to the channel.
   */
  private Bell<Void> write(final ByteBuffer bytes) {
    return channel.new AsBell<Void>() {
      public Bell<Void> convert(SocketChannel ch) throws Exception {
        ch.write(bytes);
        if (!bytes.hasRemaining())
          return Bell.rungBell();
        Bell<Void> pause = selector.onWritable().new AsBell<Void>() {
          public Bell<Void> convert(SocketChannel ch) { return write(bytes); }
        };
        pause(pause);
        return pause;
      } public void fail(Throwable t) {
        close(t);
      }
    };
  }

  protected void doClose() throws Exception {
    if (channel != null) {
      if (!channel.isDone())
        channel.cancel();
      else
        channel.sync().close();
    } if (server != null) {
      if (!server.isDone())
        server.cancel();
      else
        server.sync().close();
    }
  }

  public static void main(String[] args) throws Exception {
    new TCPSocket(12345) {
      public void accept(Socket socket) {
        socket.join(socket);
      }
    }.listen().onClose().sync();
  }
}
