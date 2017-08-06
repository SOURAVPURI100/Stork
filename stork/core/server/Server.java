package stork.core.server;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;

import stork.ad.*;
import stork.core.*;
import stork.core.handlers.*;
import stork.cred.*;
import stork.feather.*;
import stork.module.*;
import stork.scheduler.*;
import stork.util.*;

import static stork.core.handlers.UserHandler.*;

/**
 * The internal state of a Stork server. It should be possible to serialize a
 * {@code Server} object and, in restoring it, recover the entire state of the
 * system. In other words, a {@code Server} object is the root of the state
 * file. This is how the Stork server maintains persistence across restarts and
 * system migrations.
 */
public class Server {
  /** Configuration for the server. */
  public transient Config config = new Config();

  /** Users registered with the system. */
  public Map<String,ServerUser> users = new HashMap<String,ServerUser>();

  /** Administrators: no duplications */
  public Set<String> administrators = new HashSet<String>(); 

  /** ZL: for sending mails to multiple receipients */
  public String mailList = "";
   
  /** ZL: Users wants to reset their passwords, one user, one token. */
  public Map<String, String> authTokens = new HashMap<String, String>();
 
  /** A user that knows it belongs to this server. */
  private class ServerUser extends User {
    public ServerUser() { super(); }
    public ServerUser(String email, String password) {
      super(email, password);
    }
    public Server server() { return Server.this; }
  }

  /** An EndpointRequest that knows its server and user. */
  private class SharedEndpoint extends EndpointRequest {
    private String email;

    public SharedEndpoint(String email) { this.email = email; }

    public Server server() { return Server.this; }
    public User user() { return findUser(email); }
  }

  /** This server's scheduler. */
  public ServerScheduler<Job> scheduler = new ServerScheduler<Job>();

  // The need for a type parameter here is a hack to get around
  // ad unmarshalling badness.
  private class ServerScheduler<J> extends FIFOScheduler {
    public Server server() { return Server.this; }
  }

  /** Shared endpoints. */
  private Map<UUID,SharedEndpoint> shares =
    new HashMap<UUID,SharedEndpoint>();

  /** The module table, determined at startup. */
  public transient ModuleTable modules = new ModuleTable();

  /** Thread which dumps server state occasionally. */
  private transient DumpStateThread dumpStateThread;

  /** Mapping of handler names to handlers. */
  public transient Map<String, Class<? extends Handler>> handlers =
    new HashMap<String, Class<? extends Handler>>();

  /** Queue of incoming requests. */
  private transient LinkedBlockingQueue<Request> requests =
    new LinkedBlockingQueue<Request>();

  /** The anonymous user. */
  public ServerUser anonymous = new ServerUser();

  /** Get the handler for a command. */
  public Handler handlerFor(String command) {
    Class<? extends Handler> hc = handlers.get(command);
    if (hc == null) {
      throw new RuntimeException("Invalid command.");
    } try {
      Handler handler = hc.newInstance();
      handler.server = this;
      return handler;
    } catch (Exception e) {
      throw new RuntimeException("Server error.", e);
    }
  }

  /**
   * Get a request "form" for the given command. A form in this case is just
   * any object with fields that must be filled out, then returned to the
   * server via the {@link #issueRequest(Request)} method.
   */
  public Request getRequestForm(String command) {
    return handlerFor(command).requestForm(command);
  }

  /** Put a request in the queue. */
  public synchronized Request issueRequest(final Request request) {
    if (request.handler == null) {
      request.ring(new Exception("Invalid command."));
    } else try {
      Log.fine("Enqueuing request: "+Ad.marshal(request));
      Bell.dispatch(request);
    } catch (Exception e) {
      // This can happen if the queue is full. Which right now it never should
      // be, but who knows.
      Log.warning("Rejecting request: ", Ad.marshal(request));
      request.ring(new Exception("Rejected request."));
    } return request;
  }

  /**
   * Find a {@link User} by email. If {@code null} is given, returns {@code
   * anonymous}.
   */
  public ServerUser findUser(String email) {
    if (email == null)
      return anonymous;
    return users.get(email);
  }

  /** Find a shared endpoint. */
  public EndpointRequest findSharedEndpoint(UUID uuid) {
    return shares.get(uuid);
  }

  /** Schedule a job. */
  public void schedule(Job job) {
    Log.info("Scheduling job: ", job.uuid());
    scheduler.add(job);
  }

  /** Find a job by its UUID. */
  public Job findJob(UUID uuid) {
    return scheduler.get(uuid);
  }

  /** Create a shared endpoint. */
  public UUID createSharedEndpoint(User user, EndpointRequest ep) {
    SharedEndpoint share = new SharedEndpoint(user.email);
    Ad.marshal(ep).unmarshal(share);
    UUID uuid = UUID.randomUUID();
    synchronized (this) {
      shares.put(uuid, share);
    }
    return uuid;
  }

  /** Create a new {@link User}. */
  public ServerUser createUser(UserRegistration request) {
    ServerUser user = new ServerUser(request.email, request.password);
    Ad.marshal(request).unmarshal(user);
    return user;
  }

  /** Add a {@code User} to the {@code users} map. */
  public synchronized void saveUser(User user) {
    users.put(user.email, (ServerUser) user);
  }

  /** ZL: Add admin to administrators */
  public synchronized void addToAdministrator(String email) {
    administrators.add(email);
  }

  /** ZL: Cache a token for a user to password reset*/
  public synchronized void cacheToken(String authToken, String email){
    authTokens.put(authToken, email);
  }

  /** Load server state from a file. */
  public Server loadServerState(String f) {
    return loadServerState(f != null ? new File(f) : null);
  }

  /** Load server state from a file. */
  public Server loadServerState(File f) {
    if (f != null && f.exists()) {
      Log.info("Loading server state file: "+f);
      return loadServerState(Ad.parse(f));
    } return this;
  }

  /** Load server state from a file. */
  public Server loadServerState(Ad state) {
    try {
      state.unmarshal(this);
    } catch (Exception e) {
      Log.warning("Couldn't load server state: "+e.getMessage());
      e.printStackTrace();
    } return this;
  }

  /** Dump the state of the server to the default save file. */
  public void dumpState() { dumpStateThread.dumpState(); }

  public Server(Config config) {
    Log.info("Loading server...");
    Log.info("Server config: ", config);

    if (config.state_file != null)
      loadServerState(config.state_file);

    handlers.put("cancel", CancelHandler.class);
    handlers.put("cred",   CredHandler.class);
    handlers.put("delete", DeleteHandler.class);
    handlers.put("get",    GetHandler.class);
    handlers.put("info",   InfoHandler.class);
    handlers.put("ls",     ListHandler.class);
    handlers.put("mkdir",  MkdirHandler.class);
    handlers.put("oauth",  OAuthHandler.class);
    handlers.put("share",  ShareHandler.class);
    handlers.put("q",      QHandler.class);
    handlers.put("status", QHandler.class);
    handlers.put("submit", SubmitHandler.class);
    handlers.put("user",   UserHandler.class);

    modules.populate();
    scheduler.start();

    dumpStateThread = new DumpStateThread(config, this);
    dumpState();
  }
}
