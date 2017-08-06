package stork.feather.net;

import stork.feather.*;

/**
 * An asynchrounous network connection. For connection-oriented sockets, this
 * can be used either to make an outgoing connection, or to accept incoming
 * connections (using the {@link #accept(Socket)} method). For connectionless
 * sockets, this can be used to either send data or receive incoming data.
 */
public abstract class Socket extends Coder<byte[],byte[]> {
  private Bell<?> connectBell, listenBell;

  /** Will ring after the socket is connected. */
  private final Bell<Void> onConnect = new Bell<Void>() {
    public void fail(Throwable t) { doClose.ring(t); }
  };

  /** Will ring after the socket is listening. */
  private final Bell<Void> onListen = new Bell<Void>() {
    public void fail(Throwable t) { doClose.ring(t); }
  };

  /** Ring this to start the closing process. */
  private final Bell<Void> doClose = new Bell<Void>() {
    public void always() {
      if (connectBell != null)
        connectBell.cancel();
      if (listenBell != null)
        listenBell.cancel();
      try {
        doClose();
      } catch (Exception e) {
        // Ignore.
      }
    }
  };

  /** Start an outgoing connection. */
  public final synchronized Socket connect() {
    if (connectBell == null) try {
      connectBell = doConnect();
      connectBell.as((Void) null).promise(onConnect);
    } catch (Exception e) {
      connectBell = Bell.wrap(e);
    } return this;
  }

  /** Start the connection process when {@code bell} rings. */
  public final Socket connectOn(Bell<?> bell) {
    bell.new Promise() {
      public void done() { connect(); }
    };
    return this;
  }

  /** Return a bell that rings when the socket is opened. */
  public final Bell<Socket> onConnect() {
    return onConnect.as(this);
  }

  /** Listen for incoming connections or data. */
  public final synchronized Socket listen() {
    if (listenBell == null) try {
      listenBell = doListen();
      listenBell.as((Void) null).promise(onListen);
    } catch (Exception e) {
      listenBell = Bell.wrap(e);
    } return this;
  }

  /** Start listening when {@code bell} rings. */
  public final Socket listenOn(Bell<?> bell) {
    bell.new Promise() {
      public void done() { listen(); }
    };
    return this;
  }

  /** Return a bell that rings when the socket is opened. */
  public final Bell<Socket> onListen() {
    return onListen.as(this);
  }

  /**
   * Handle an incoming connection. This will only be called on
   * connection-oriented sockets after listening has begun. Throwing an
   * exception here will cause the socket to be closed.
   *
   * @param socket a {@code Socket} which has just been accepted.
   */
  protected void accept(Socket socket) {
    throw new RuntimeException();
  }

  /** Close the socket. */
  public final Socket close() {
    doClose.ring();
    return this;
  }

  /** Close the socket for the given reason. */
  public final Socket close(Throwable reason) {
    doClose.ring(reason);
    return this;
  }

  /** Close the socket when {@code bell} rings. */
  public final Socket closeOn(Bell<?> bell) {
    bell.as((Void) null).promise(doClose);
    return this;
  }

  /** Return a bell that rings when the socket is opened. */
  public final Bell<Socket> onClose() { return doClose.as(this); }

  /** Check if the channel is closed. */
  public final boolean isClosed() { return doClose.isDone(); }

  /**
   * Handle asynchronously connecting the socket. This will be called at most
   * once.
   *
   * @return A bell which should ring when the connection has been established
   * or has failed to be established. If cancelled, the connection process
   * should be halted.
   * @throws Exception If a problem happens immediately.
   */
  protected abstract Bell<?> doConnect() throws Exception;

  /**
   * Handle starting listening on the socket. This will be called at most once.
   *
   * @return A bell which should ring when the socket has begun listening or
   * has failed to start listening. If cancelled, the connection process should
   * be halted.
   * @throws Exception If a problem happens immediately.
   */
  protected abstract Bell<?> doListen() throws Exception;

  /**
   * Handle closing this socket. This will be called at most once.
   *
   * @throws Exception Any exception thrown will be ignored.
   */
  protected abstract void doClose() throws Exception;
}
