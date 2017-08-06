package stork.module.ftp;

import java.net.*;
import java.util.*;
import java.nio.charset.*;

import io.netty.bootstrap.*;
import io.netty.channel.*;
import io.netty.buffer.*;
import io.netty.channel.nio.*;
import io.netty.channel.socket.*;
import io.netty.channel.socket.nio.*;
import io.netty.handler.codec.*;
import io.netty.handler.codec.string.*;
import io.netty.handler.codec.base64.*;
import io.netty.util.*;
import io.netty.util.concurrent.*;

import org.ietf.jgss.*;
import org.gridforum.jgss.*;

import stork.feather.*;
import stork.feather.errors.*;
import stork.feather.URI;
import stork.util.*;

/**
 * An abstraction of an FTP control channel. This class takes care of command
 * pipelining, extracting replies, and maintaining channel state. Right now,
 * the channel is mostly concurrency-safe, though issues could arise if
 * arbitrary commands entered the pipeline during a command sequence. A
 * simplisitic channel locking mechanism exists which can be used for
 * synchronized access to the channel, but it is still ill-advised to have more
 * than one subsystem issuing commands through the same channel at the same
 * time.
 */
public class FTPChannel {
  // The maximum amount of time (in ms) to wait for a connection.
  static final int timeout = 2000;

  private final Bell<Void> onClose = new Bell<Void>();

  // FIXME: We should use something system-wide.
  static EventLoopGroup group = new NioEventLoopGroup(1);

  // Used for GSS authentication.
  private final String host;

  // Internal representation of the remote server type.
  private static enum Protocol {
    ftp(21), gridftp(2811), gsiftp(2811);

    int port;

    Protocol(int def_port) { port = def_port; }

    public boolean isGSI() {
      switch (this) {
        case gridftp:
        case gsiftp:
          return true;
        default:
          return false;
      }
    }
  }

  // Doing this allows state to more easily be shared between views of the same
  // channel.
  FTPSharedChannelState data;

  class FTPSharedChannelState {
    Protocol protocol;
    ChannelFuture future;
    SecurityContext security;
    Deque<Command> handlers = new ArrayDeque<Command>();

    FTPChannel owner;  // The view that owns the underlying channel.

    // Any FTP server that adheres to specifications will use UTF-8, but let's
    // not preclude the possibility of being able to configure the encoding.
    Charset encoding = CharsetUtil.UTF_8;

    Reply welcome;
    FeatureSet features = new FeatureSet();

    Bell<Character> mode = new Bell<Character>('S');
    Bell<Character> type = new Bell<Character>('A');

    // Whether or not we prefer to put the server in passive mode.
    boolean preferPassive = true;
  }

  // Deferred commands
  Deque<Deferred> deferred = new ArrayDeque<Deferred>();

  public FTPChannel(String uri) {
    this(URI.create(uri));
  } public FTPChannel(URI uri) {
    this(uri.protocol(), uri.host(), null, uri.port());
  } public FTPChannel(String host, int port) {
    this(null, host, null, port);
  } public FTPChannel(InetAddress addr, int port) {
    this(null, null, addr, port);
  } public FTPChannel(String proto, String host, int port) {
    this(proto, host, null, port);
  } public FTPChannel(String proto, InetAddress addr, int port) {
    this(proto, null, addr, port);
  }

  // The above constructors delegate to this.
  private FTPChannel(String proto, String host, InetAddress addr, int port) {
    data = new FTPSharedChannelState();
    data.owner = this;

    this.host = (host != null) ? host : addr.toString();

    data.protocol = (proto == null) ?
      Protocol.ftp : Protocol.valueOf(proto.toLowerCase());
    if (port <= 0) port = data.protocol.port;

    Bootstrap b = new Bootstrap();
    b.group(group).channel(NioSocketChannel.class).handler(new Initializer());

    if (host != null)
      data.future = b.connect(host, port);
    else
      data.future = b.connect(addr, port);
  }

  // This special constructor is used internally to create channel views.
  // Calling this constructor will enqueue a synchronization command that will
  // allow the channel to assume control when it is reached.
  private FTPChannel(FTPChannel parent) {
    data = parent.data;
    host = parent.host;
  }

  // Handles initializing the control channel connection and attaching the
  // necessary codecs.
  class Initializer extends ChannelInitializer<SocketChannel> {
    public void initChannel(SocketChannel ch) throws Exception {
      ch.config().setConnectTimeoutMillis(timeout);

      ChannelPipeline p = ch.pipeline();

      p.addLast("reply_decoder", new ReplyDecoder());
      p.addLast("reply_handler", new ReplyHandler());

      p.addLast("command_encoder", new CommandEncoder());
    }
  }

  // A reply from the server.
  public class Reply {
    public final int code;
    private final List<String> lines;

    private Reply(int code, List<String> lines) {
      if (code < 100 || code >= 700)
        throw new RuntimeException("Bad reply code: "+code);
      this.code = code;
      this.lines = Collections.unmodifiableList(lines);
    }

    // Get the number of lines.
    public int length() { return lines.size(); }

    // Get a line by number.
    public String line(int i) { return lines.get(i); }

    // Get an array of all the lines as strings.
    public List<String> lines() { return lines; }

    // Return a description meaningful to a human based on a reply code.
    public String description() {
      return FTPMessages.fromCode(code);
    }

    // Return an exception based on the reply.
    public RuntimeException asError() {
      return new RuntimeException(description());
    }

    // Get the message as a string. If the reply is one line, the message is
    // the first line. If it's multi-lined, the message is the part between the
    // first and last lines.
    public String message() {
      if (length() <= 1)
        return line(0);
      StringBuilder sb = new StringBuilder();
      for (int i = 1, z = length()-1; i < z; i++) {
        sb.append(line(i));
        if (i != z) sb.append('\n');
      }
      return sb.toString();
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0, z = length()-1; i <= z; i++) if (i != z)
        sb.append(code).append('-').append(line(i)).append('\n');
      else
        sb.append(code).append(' ').append(line(i));
      return sb.toString();
    }

    // Returns true if this reply is intermediate and further replies are
    // expected before the command has been fulfilled.
    public boolean isPreliminary() {
      return code/100 == 1;
    }

    // Returns true if the reply indicates the command completed successfully.
    public boolean isComplete() {
      return code/100 == 2;
    }

    // Returns true if the reply indicates the command cannot be carried out
    // unless followed up with another command.
    public boolean isIncomplete() {
      return code/100 == 3;
    }

    // Returns true if the reply is negative (i.e., it indicates a failure).
    public boolean isNegative() {
      return code/100 == 4 || code/100 == 5;
    }

    // Returns true if the message is protected, i.e. its message is a payload
    // containing a specially encoded reply. In most cases, users will not see
    // replies of this type directly as the channel handlers will transparently
    // decode them. However, this can happen if server behaves improperly by
    // sending replies of this type with no security context set.
    public boolean isProtected() {
      return code/100 == 6;
    }
  }

  /** Eats ASCII bytes, emits lines. */
  abstract class LineCodec {
    StringBuilder buffer = new StringBuilder();

    public final void feed(byte[] bs) {
      for (byte b : bs) switch (b) {
        case '\n':
          emit(buffer.toString());
          buffer = new StringBuilder();
        case '\r':
          break;
        default:
          buffer.append((char) b);
      }
    }

    public abstract void emit(String s);
  }

  /** A parser for FTP replies. */
  abstract class ReplyCodec {
    /** Reply lines are buffered in here. */
    List<String> lines = new LinkedList<String>();
    String codestr;
    int code;

    /** Feed a line into the parser. */
    public final void feed(String line) {
      try {
        Reply reply = decodeLine(line);
        if (reply != null)
          emit(reply);
      } catch (Exception e) {
        throw new RuntimeException("Bad line from server: "+line, e);
      }
    }

    /** This is called when a reply has been parsed. */
    public abstract void emit(Reply r);

    protected synchronized Reply decodeLine(String line) throws Exception {
      char sep = '-';

      // Extract the reply code and separator.
      if (lines.isEmpty()) {
        codestr = line.substring(0, 3);
        code = Integer.parseInt(codestr);
        sep = line.charAt(3);
        line = line.substring(4);
      } else if (line.length() >= 4) {
        // Some servers insert "xyz-" at the beginning of additional reply
        // lines. This strips that.
        String s = line.substring(0, 4);
        sep = s.charAt(3);
        if (s.startsWith(codestr) && (sep == '-' || sep == ' '))
          line = line.substring(4);
        else
          sep = '-';
      }

      // Save the rest of the message.
      lines.add(line);

      // Act based on the separator.
      if (sep == ' ') {
        Reply r = new Reply(code, lines);
        lines = new LinkedList<String>();
        return r;
      } else if (sep == '-') {
        return null;
      } else {
        throw new RuntimeException("Unexpected: "+sep);
      }
    }
  }

  // Handles decoding replies from the server.
  class ReplyDecoder extends ByteToMessageDecoder {
    ChannelHandlerContext ctx;
    MyCodec codec = new MyCodec() {
      public void emit(Reply reply) { handleReply(reply); }
    };
    MyCodec protCodec = new MyCodec() {
      public void emit(Reply reply) { ctx.fireChannelRead(reply); }
    };

    // Combined LineCodec and ReplyCodec.
    abstract class MyCodec {
      public final void feed(byte[] bytes) {
        lineCodec.feed(bytes);
      }
      LineCodec lineCodec = new LineCodec() {
        public void emit(String line) { replyCodec.feed(line); }
      };
      ReplyCodec replyCodec = new ReplyCodec() {
        public void emit(Reply reply) { MyCodec.this.emit(reply); }
      };
      public abstract void emit(Reply reply);
    }

    // Feed incoming bytes to the reply codec.
    public void decode(ChannelHandlerContext ctx, ByteBuf buffer, List out) {
      this.ctx = ctx;
      codec.feed(new Slice(buffer).asBytes());
      buffer.clear();
    }

    // Got a reply. Decode if necessary. Otherwise pass down.
    private void handleReply(Reply reply) {
      if (data.security != null)
        decodeProtected(reply);
      else
        ctx.fireChannelRead(reply);
    }

    // Decode a protected payload and feed to the line parser.
    private void decodeProtected(Reply reply) {
      if (!reply.isProtected()) throw new
        RuntimeException("Unprotected reply on protected channel.");

      for (String s : reply.lines()) try {
        ByteBuf eb = Unpooled.wrappedBuffer(s.getBytes(data.encoding));
        ByteBuf db = data.security.unprotect(Base64.decode(eb));
        protCodec.feed(new Slice(db).asBytes());
      } catch (Exception e) {
        throw new RuntimeException("Bad reply from server.", e);
      }
    }
  }

  // Handles replies as they are received.
  class ReplyHandler extends SimpleChannelInboundHandler<Reply> {
    public boolean acceptInboundMessage(Object msg) {
      return msg instanceof Reply;
    }

    public void messageReceived(ChannelHandlerContext ctx, Reply reply) {
      Log.finer("Got: ", reply);
      switch (reply.code) {
        case 220:
          if (data.welcome == null)
            data.welcome = reply;
          break;
        case 421:
          Log.fine("Channel closing due to 421.");
          FTPChannel.this.close();
          break;
        default:
          feedHandler(reply);
      }
    }

    public void channelInactive(ChannelHandlerContext ctx) {
      Log.fine("Channel closed by peer.");
      FTPChannel.this.close();
    }

    // TODO: How should we handle exceptions? Which exceptions can this thing
    // receive anyway?
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable t) {
      Log.fine("Channel closed by exception.");
      t.printStackTrace();
      FTPChannel.this.close();
    }
  }

  // A security context represents all of the state of the channel with respect
  // to security and provides methods for encoding and decoding messages.
  interface SecurityContext {
    // Returns true if the session has been established.
    boolean established();

    // Given an input token, return an output token which should be given to
    // the remote server via ADAT, or null if the session is established.
    ByteBuf handshake(ByteBuf in) throws Exception;

    // Given a byte buffer containing protected bytes (not a Base64 encoding
    // thereof), decode the bytes back into the plaintext payload.
    ByteBuf unprotect(ByteBuf buf) throws Exception;

    // Given a byte buffer containing some information, generate a command with
    // a payload protected according to the current security context.
    ByteBuf protect(ByteBuf buf) throws Exception;
  }

  // A security context based on GSSAPI.
  class GSSSecurityContext implements SecurityContext {
    GSSContext context;

    public GSSSecurityContext(GSSCredential cred) throws GSSException {
      GSSManager manager = ExtendedGSSManager.getInstance();
      Oid oid = new Oid("1.3.6.1.4.1.3536.1.1");
      GSSName peer = manager.createName(
        "host@"+host, GSSName.NT_HOSTBASED_SERVICE);
      context = manager.createContext(
        peer, oid, cred, cred.getRemainingLifetime());
      context.requestCredDeleg(true);
      context.requestConf(true);
    }

    // Utility method for extracting a buffer as a byte array.
    private byte[] bytes(ByteBuf buf) {
      byte[] b;
      if (buf.hasArray())
        b = buf.array();
      else
        buf.getBytes(buf.readerIndex(), b = new byte[buf.readableBytes()]);
      return b;
    }

    public boolean established() {
      return context.isEstablished();
    }

    public ByteBuf handshake(ByteBuf in) throws GSSException {
      if (established())
        return null;
      byte[] i = bytes(in);
      byte[] o = context.initSecContext(i, 0, i.length);
      return Unpooled.wrappedBuffer(o);
    }

    public ByteBuf unprotect(ByteBuf buf) throws GSSException {
      byte[] b = bytes(buf);
      return Unpooled.wrappedBuffer(context.unwrap(b, 0, b.length, null));
    }

    public ByteBuf protect(ByteBuf buf) throws GSSException {
      byte[] b = bytes(buf);
      return Unpooled.wrappedBuffer(context.wrap(b, 0, b.length, null));
    }
  }

  // Handles encoding commands using the current charset and security
  // mechanism. We can assume every message is a single command with no
  // newlines.
  class CommandEncoder extends MessageToMessageEncoder<Object> {

    protected void encode(ChannelHandlerContext ctx, Object msg, List out)
    throws Exception {
      ByteBuf ENC =
        Unpooled.wrappedBuffer("ENC ".getBytes(data.encoding));
      ByteBuf RN =
        Unpooled.wrappedBuffer("\r\n".getBytes(data.encoding));

      Log.finer("Sending command: ", msg);
      ByteBuf raw =
        Unpooled.wrappedBuffer(msg.toString().getBytes(data.encoding));

      if (data.security != null) {
        ByteBuf eb = Base64.encode(data.security.protect(raw), false);
        ctx.write(ENC);
        ctx.write(eb);
      } else {
        ctx.write(raw);
      }

      ctx.writeAndFlush(RN);
    }
  }

  // Used internally to extract the channel from the future.
  private Channel channel() {
    try {
      return data.future.syncUninterruptibly().channel();
    } catch (java.nio.channels.UnresolvedAddressException e) {
      throw new RuntimeException("Host could not be resolved.");
    }
  }

  // Close the channel and run the onClose handler.
  public synchronized void close() {
    if (!isClosed()) {
      channel().close();
      onClose.ring();
    }
  }

  // Check if the channel has been closed.
  public synchronized boolean isClosed() {
    return onClose.isDone();
  }

  // This gets called when the channel is closed by some means. Subclasses
  // should implement this.
  public Bell<?> onClose() {
    return onClose;
  }

  // Try to authenticate using a username and password.
  public Bell<Reply> authorize() {
    return authorize("anonymous", "");
  }

  // Try a passwordless login with a username.
  public Bell<Reply> authorize(String user) {
    return authorize(user, "");
  }

  // Try to authenticate using a username and password.
  public Bell<Reply> authorize(final String user, final String pass) {
    return new Command("USER", user).new AsBell<Reply>() {
      // Handle the USER command reply.
      public Bell<Reply> convert(Reply r) {
        if (r.code == 331)
          return new Command("PASS", pass);
        if (r.isComplete())
          return Bell.wrap(r);
        throw r.asError();
      }
    }.new As<Reply>() {
      // Handle the PASS command reply (or 2xx reply to USER).
      public Reply convert(Reply r) {
        if (r.isComplete())
          return r;
        throw r.asError();
      }
    }.new As<Reply>() {
      // Handle whatever error occurred by asking for login.
      public Reply convert(Throwable r) {
        if (data.protocol.isGSI())
          throw new AuthenticationRequired("gss");
        else
          throw new AuthenticationRequired("userinfo");
      } public Reply convert(Reply r) {
        return r;
      }
    };
  }

  /**
   * Authenticate when the credential becomes available. This should be done
   * from inside a locked view.
   */
  public Bell<Reply> authenticate(Bell<GSSCredential> cred) {
    return cred.new AsBell<Reply>() {
      public Bell<Reply> convert(GSSCredential cred) throws Exception {
        Log.fine("Authenticating with: ", cred);
        return authenticate(cred);
      } public void done(Reply r) {
        Log.fine("Authentication successful: ", r);
      } public void fail(Throwable t) {
        Log.fine("Authentication failed: ", t);
      }
    };
  }

  /**
   * Try to authenticate with a GSS credential. This should be done from inside
   * a locked view.
   */
  public Bell<Reply> authenticate(GSSCredential cred) throws Exception {
    final GSSSecurityContext sec;

    if (data.security != null)
      throw new RuntimeException("Already authenticated");

    sec = new GSSSecurityContext(cred);

    return new Command("AUTH GSSAPI").new AsBell<Reply>() {
      public Bell<Reply> convert(Reply r) throws Exception {
        if (r.isNegative())
          throw r.asError();
        if (r.isIncomplete())
          return handshake(sec, Unpooled.EMPTY_BUFFER);
        return Bell.wrap(r);
      } public void done() {
        data.security = sec;
      }
    };
  }

  // Handshake procedure for all authentication types. The input byte buffer
  // should be the raw binary token, not a Base64 encoding.
  private Bell<Reply> handshake(final SecurityContext sec, ByteBuf it) {
    final Bell<Reply> bell = new Bell<Reply>();

    try {
      ByteBuf ot = Base64.encode(sec.handshake(it), false);

      Log.finer("Sending ADAT: ", ot);
      new Command("ADAT", ot.toString(data.encoding)) {
        public void done(Reply r) throws Exception {
          if (r.isIncomplete()) {
            String line = r.message().substring(5);
            ByteBuf bb = Unpooled.wrappedBuffer(line.getBytes(data.encoding));
            ByteBuf token = Base64.decode(bb);
            handshake(sec, token).promise(bell);
          } else if (r.isComplete()) {
            promise(bell);
          } else {
            throw r.asError();
          }
        }
      };
    } catch (Exception e) {
      Log.fine("ADAT failed: ", e);
      bell.ring(e);
    }

    return bell;
  }

  // Features assumed to exist on all FTP servers.
  private static String[] defaultCommands = {
    "ABOR", "ACCT", "ALLO", "APPE", "CDUP", "CWD",  "DELE", "HELP", "LIST",
    "MKD",  "MODE", "NLST", "NOOP", "PASS", "PASV", "PORT", "PWD",  "QUIT",
    "REIN", "REST", "RETR", "RMD",  "RNFR", "RNTO", "SITE", "SMNT", "STAT",
    "STOR", "STOU", "STRU", "SYST", "TYPE", "USER"
  };

  // A structure used to hold a set of features supported by a server. A fine
  // specimen of overengineering.
  // TODO: This should probably propagate control channel errors.
  class FeatureSet {
    private Map<String,Bell> features;

    // This promises all bells produced by this object. It can be canceled to
    // cancel all remaining bells.
    private final Bell finalBell = new Bell();

    // Asynchronously check if a command is supported.
    private synchronized void init() {
      if (features != null)
        return;

      features = new HashMap<String,Bell>();

      // Add all features guaranteed to exist.
      for (String cmd : defaultCommands)
        addFeature(cmd);

      // ...and pipe all the check commands.
      new Command("HELP") {
        public void done(Reply r) {
          if (!r.isComplete()) return;
          for (int i = 1; i < r.length()-1; i++)
          for (String c : r.line(i).trim().toUpperCase().split(" "))
            if (!c.contains("*")) addFeature(c);
        }
      };
      new Command("HELP SITE") {
        public void done(Reply r) {
          if (!r.isComplete()) return;
          for (int i = 1; i < r.length()-1; i++)
            addFeature(r.line(i).trim().split(" ", 2)[0].toUpperCase());
        }
      };
      //new Command("FEAT");  // TODO

      // Once all the commands have finished, fail the remaining bells.
      new Command(null) {
        public void done() { finalBell.cancel(); }
      };
    }

    private synchronized Bell bellFor(String cmd) {
      init();
      Bell bell = features.get(cmd);
      if (bell == null) {
        features.put(cmd, bell = new Bell());
        finalBell.promise(bell);
      }
      return bell;
    }

    public synchronized Bell supports(String cmd) {
      return bellFor(cmd);
    }

    private synchronized void addFeature(String cmd) {
      bellFor(cmd).ring();
    }
  }

  // Checks if a command is supported by the server.
  public Bell<Boolean> supports(String cmd) {
    return data.features.supports(cmd).as(true, false);
  } private Bell[] supportsMulti(String... cmd) {
    Bell[] bells = new Bell[cmd.length];
    for (int i = 0; i < cmd.length; i++)
      bells[i] = data.features.supports(cmd[i]);
    return bells;
  } public Bell<Boolean> supportsAny(String... cmd) {
    return Bell.any(supportsMulti(cmd)).as(true, false);
  } public Bell<Boolean> supportsAll(String... cmd) {
    return Bell.all(supportsMulti(cmd)).as(true, false);
  }

  // Append the given command to the handler queue. If the command is a sync
  // command and there is nothing else in the queue, just fulfill it
  // immediately and don't put it in the queue.
  private void addHandler(Command c) {
    synchronized (data.handlers) {
      if (!c.isSync() || !data.handlers.isEmpty()) {
        data.handlers.add(c);
        return;
      }
    }

    // If we didn't return, it's a sync. Handling a sync is fast, but we should
    // release handlers as soon as possible.
    c.ring();
  }

  // "Feed" the topmost handler a reply. If the reply is a preliminary reply,
  // it will pop the command handler and any sync commands between it and the
  // next non-sync command (or the end of the queue). In order to release the
  // monitor on the queue as soon as possible, the command handlers are not
  // called until after the queue has been modified.
  private void feedHandler(Reply reply) {
    Command handler;
    List<Command> syncs = null;

    // We should try to release this as soon as possibile, so don't do anything
    // that might take a long time in here.
    synchronized (data.handlers) {
      assert !data.handlers.isEmpty();

      if (reply.isPreliminary()) {
        handler = data.handlers.peek();
      } else {
        handler = data.handlers.pop();

        // Remove all the syncs.
        Command peek = data.handlers.peek();
        if (peek != null && peek.isSync()) {
          syncs = new LinkedList<Command>();
          do {
            syncs.add(peek);
            data.handlers.pop();
            peek = data.handlers.peek();
          } while (peek != null && peek.isSync());
        }
      }
    }

    Log.finer("Feeding handler: ", handler);

    // Now we can call the handlers.
    if (reply.isPreliminary())
      handler.handle(reply);
    else
      handler.ring(reply);
    if (syncs != null) for (Command sync : syncs)
      sync.ring();
  }

  /**
   * Change the channel data type.
   * @return The data type after this command.
   */
  public synchronized Bell<Character> type(char t) {
    return data.type =
      new Command("TYPE", t).expectComplete().as(t).or(data.type);
  }

  /**
   * Change the channel transfer mode.
   * @return The transfer mode after this command.
   */
  public synchronized Bell<Character> mode(char m) {
    return data.mode =
      new Command("MODE", m).expectComplete().as(m).or(data.mode);
  }

  /** Negotiate a passive mode data channel. */
  public synchronized Bell<FTPHostPort> passive() {
    return new Command("PASV").expectComplete().new As<FTPHostPort>() {
      public FTPHostPort convert(Reply r) { return new FTPHostPort(r); }
    };
  }

  /**
   * Instantiating this class requests a lock on the channel and returns a
   * special view of the channel once the lock request is satisfied. The
   * special channel view will have exclusive use of the channel until unlocked
   * (after which the channel lock will become unusable) or garbage collected.
   * The channel should be unlocked as soon as the command sequence requiring
   * synchronicity has been issued. Commands written to any other channels
   * during this time will have their commands deferred and sent after the
   * channel has been unlocked.
   */
  public class Lock extends FTPChannel {
    public Lock() {
      super(FTPChannel.this);
      FTPChannel.this.new Command(null) {
        public void done() { Lock.this.assumeControl(); }
      };
    }

    /** The channel may not be locked through a locked view. */
    public Bell<FTPChannel> lock() {
      throw new IllegalStateException("Cannot lock from a view");
    }

    /** Give control back to the parent channel. */
    public void unlock() {
      new Command(null) {
        public void done() { FTPChannel.this.assumeControl(); }
      };
    }

    protected void finalize() {
      if (data.owner == this)
        FTPChannel.this.assumeControl();
    }
  }

  // This is called whenever it's the the channel's turn to become the channel
  // owner.
  protected synchronized void assumeControl() {
    synchronized (data) {
      Log.finer(data.owner.hashCode()+" -> "+hashCode());
      data.owner = this;
      if (!deferred.isEmpty()) {
        Deque<Deferred> realDeferred = deferred;
        deferred = new LinkedList<Deferred>();

        for (Deferred d : realDeferred) d.send();

        realDeferred.clear();
        realDeferred.addAll(deferred);
        deferred = realDeferred;
      }
    }
  }

  // Release the lock, if this channel is a locked view. If it's not (and the
  // base channel never is), this throws an exception.
  public void unlock() {
    throw new IllegalStateException("channel is not locked");
  }

  // A deferred command which holds onto its arguments until it is ready to be
  // written. Once it's ready to be written, call send(). If the view is not
  // the owner when send() is called, the command will once again be deferred.
  private class Deferred {
    final Command cmd;
    final Object verb;
    final Object[] args;

    Deferred(Command c, Object v, Object[] a) {
      cmd = c; verb = v; args = a;
    } void send() {
      // If we're not the owner, defer the command. Otherwise, send it.
      if (data.owner != FTPChannel.this) {
        deferred.add(this);
        Log.finer(FTPChannel.this.hashCode()+": Deferring "+this);
      } else {
        addHandler(cmd);
        if (verb != null) channel().writeAndFlush(this);
        Log.finer(FTPChannel.this.hashCode()+": Sending "+this);
      }
    }

    public String toString() {
      if (verb == null)
        return "(sync)";
      StringBuilder sb = new StringBuilder(verb.toString());
      for (Object o : args)
        sb.append(" ").append(o);
      return sb.toString();
    }
  }

  /**
   * This is sort of the workhorse of the channel. Instantiate one of these to
   * send a command across this channel. The newly instantiated object will
   * serve as a "future" for the server's ultimate reply. For example, a simple
   * authorization routine could be written like:
   *
   *   FTPChannel ch = new FTPChannel(...);
   *   Reply r = ch.new Command("USER bob") {
   *     public void done(Reply r) {
   *       if (r.code == 331)
   *         new Command("PASS monkey");
   *     }
   *   }.sync();
   *
   * This class's constructor will cause the command to be written to the
   * channel and placed in the handler queue. This class can be anonymously
   * subclassed to write command handlers inline, or can be subclassed normally
   * for repeat issue of the command.
   */
  public class Command extends Bell<Reply> {
    private final boolean isSync;
    private String debugString;

    /**
     * Constructing this will automatically cause the given command to be
     * written to the server. Typically, the passed cmd will be a string, but
     * can be anything. The passed arguments will be stringified and
     * concatenated with spaces for convenience. If {@code verb} is {@code
     * null}, nothing is actually written to the server, and the command bell
     * will ring when all commands piped prior to the sync have completed.
     */
    public Command(Object verb, Object... args) {
      synchronized (data) {
        isSync = (verb == null);
        if (isSync)
          debugString = "(sync)";
        else
          debugString = verb.toString();
        new Deferred(this, verb, args).send();
      }
    }

    /** An optional handler for intermediate replies. */
    public void handle(Reply r) { }

    /**
     * Return a bell which will fail if the reply code is not in the given
     * range (inclusive), instead of ringing successfully with the reply.
     */
    public Bell<Reply> expect(final int lo, final int hi) {
      return this.new As<Reply>() {
        public Reply convert(Reply r) throws Exception {
          if (r.code < lo || r.code > hi)
            throw r.asError();
          return r;
        }
      };
    }

    /**
     * Return a bell which will fail if the reply code does not match the given
     * code, instead of ringing successfully with the reply.
     */
    public Bell<Reply> expect(int code)   { return expect(code, code); }

    public Bell<Reply> expectComplete()   { return expect(200, 299); }

    public Bell<Reply> expectIncomplete() { return expect(300, 399); }

    public Bell<Reply> expectNegative()   { return expect(400, 599); }

    public synchronized final boolean isSync() { return isSync; }

    public String toString() {
      if (debugString != null)
        return debugString;
      return super.toString();
    }
  }

  /**
   * Asynchronous FTP data channel abstraction. Subclasses must override {@link
   * #receive(Slice)} to handle incoming data. This channel extends {@code
   * Lock}, but handles its own unlocking.
   */
  public class DataChannel extends Lock {
    private Bell<SocketChannel> dc;
    private volatile boolean read = false;
    private ChannelHandlerContext context;
    private Bell writeBell;  // Ring when we can write again.
    private Bell lastSend = Bell.rungBell();

    // Ring this to close the channel.
    private final Bell<DataChannel> onClose = new Bell<DataChannel>() {
      public void always() {
        dc.cancel().new Promise() {
          public void done(SocketChannel ch) { ch.close(); }
        };
      }
    };

    public DataChannel(char type) {
      this(type, FTPChannel.this.data.preferPassive);
    }

    public DataChannel(char type, boolean preferPassive) {
      FTPChannel.this.super();
      type(type);
      dc = preferPassive ? tryPassiveThenActive() : tryActiveThenPassive();
      dc.new AsBell<SocketChannel>() {
        public Bell<SocketChannel> convert(SocketChannel c) {
          return init().as(c);
        } public void fail(Throwable t) {
          close(t);
        } public void always() {
          DataChannel.super.unlock();
        }
      };
    }

    private Bell<SocketChannel> tryPassiveThenActive() {
      return tryPassive().new AsBell<SocketChannel>() {
        public Bell<SocketChannel> convert(SocketChannel ch) {
          return Bell.wrap(ch);
        } public Bell<SocketChannel> convert(Throwable t) {
          return tryActive();
        }
      };
    }

    private Bell<SocketChannel> tryActiveThenPassive() {
      return tryActive().new AsBell<SocketChannel>() {
        public Bell<SocketChannel> convert(SocketChannel ch) {
          return Bell.wrap(ch);
        } public Bell<SocketChannel> convert(Throwable t) {
          return tryPassive();
        }
      };
    }

    private Bell<SocketChannel> tryPassive() {
      return passive().new AsBell<SocketChannel>() {
        public Bell<SocketChannel> convert(FTPHostPort hp) {
          Bootstrap b = new Bootstrap();
          b.group(FTPChannel.group).channel(NioSocketChannel.class);
          b.handler(new ChannelInitializer<SocketChannel>() {
            public void initChannel(SocketChannel ch) throws Exception {
              ch.config().setConnectTimeoutMillis(timeout);
              ch.pipeline().addLast(new SliceHandler());
            }
          });
          return futureToBell(b.connect(hp.getAddr()));
        }
      };
    }

    private Bell<SocketChannel> tryActive() {
      return new Bell<SocketChannel>(new RuntimeException("TODO"));
    }

    // Make a future into a bell that reverse cancels.
    private Bell<SocketChannel> futureToBell(final ChannelFuture cf) {
      return new Bell<SocketChannel>() {
        {
          cf.addListener(new GenericFutureListener<ChannelFuture>() {
            public void operationComplete(ChannelFuture f) {
              try {
                ring((SocketChannel) f.channel());
              } catch (Exception e) {
                ring(e);
              }
            }
          });
        } public void fail(Throwable t) {
          cf.cancel(true);
        }
      };
    }

    // Handle incoming data chunks and forward to handler.
    // TODO: Mode E, encryption, pause/resume.
    class SliceHandler extends ChannelHandlerAdapter {
      public void channelRead(ChannelHandlerContext ctx, Object msg) {
        receive(new Slice((ByteBuf) msg));
      } public void channelInactive(ChannelHandlerContext ctx) {
        DataChannel.this.close();
      } public void read(ChannelHandlerContext ctx) {
        synchronized (FTPChannel.this) {
          if (read)
            ctx.read();
          else if (context == null)
            context = ctx;
        }
      } public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        writable(ctx.channel().isWritable());
      }
    }

    /** Called to change writability. */
    private synchronized void writable(boolean writable) {
      if (!writable && writeBell == null) {
        writeBell = new Bell();
      } else if (writable && writeBell != null) {
        writeBell.ring();
        writeBell = null;
      }
    }

    /** This must not be called externally. */
    public void unlock() {
      throw new Error("do not call unlock() on a DataChannel");
    }

    /** Return a bell which rings on connect. */
    public Bell<DataChannel> onConnect() {
      return dc.as(this);
    }

    /** Return a bell which rings on close (or failure). */
    public Bell<DataChannel> onClose() {
      return onClose;
    }

    /** Start reading data when {@code bell} rings. */
    public synchronized DataChannel startWhen(Bell bell) {
      bell.new Promise() {
        public void done() { start(); }
        public void fail(Throwable t) { close(t); }
      };
      return this;
    }

    /** Pause reading until {@code bell} rings. */
    public synchronized DataChannel pauseUntil(Bell bell) {
      if (bell != null && !bell.isDone()) {
        stop();
        startWhen(bell);
      } return this;
    }

    /** Start reading data. */
    public synchronized void start() {
      read = true;
      if (context != null)
        context.read();
      context = null;
    }

    /** Stop reading data. */
    public synchronized void stop() {
      read = false;
    }

    /** Pipe commands to be run in the lock. */
    public Bell init() { return Bell.rungBell(); }

    /** Close the channel. */
    public final void close() { onClose.ring(this); }

    /** Close the channel with a failure. */
    public final void close(Throwable t) { onClose.ring(t); }

    /** Subclasses use this to handle slices. */
    public void receive(Slice slice) { }

    /** Send a slice through the data channel. */
    public synchronized Bell send(final Slice slice) {
      return lastSend = dc.new Promise() {
        public void done(SocketChannel ch) {
          ch.writeAndFlush(slice.asByteBuf());
        }
      }.and(writeBell);
    }
  }

  public static void main(String[] args) throws Exception {
    FTPChannel ch = new FTPChannel(args[1]);
    java.io.BufferedReader r = new java.io.BufferedReader(
      new java.io.InputStreamReader(System.in));
    while (true)
      ch.new Command(r.readLine());
  }
}
