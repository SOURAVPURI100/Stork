package stork.core.server;

import java.net.IDN;
import java.security.*;
import java.util.*;

import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;

import stork.ad.*;
import stork.core.*;
import stork.cred.*;
import stork.feather.*;
import stork.scheduler.*;
import stork.util.*;
import stork.core.server.Server;

import static stork.feather.util.Time.now;

/**
 * A registered Stork user. Each user has their own view of the job queue,
 * transfer credential manager, login credentials, and user info.
 */
public abstract class User {
  /** User's email. */
  public String email;
  /** Hashed password. */
  public String hash;
  /** Salt used for hash. */
  public String salt;

  /** Set to true once the user has validated registration. */
  public boolean validated = false;
  /** The validation token we're expecting. */
  private String validationToken;
  /** Token for reset password. */
  public String authToken;
  /** Time registered */
  public long registerMoment;

  /** Previously visited URIs. */
  public LinkedList<URI> history = new LinkedList<URI>();
  /** Stored credentials. */
  public Map<UUID,StorkCred> credentials = new HashMap<UUID,StorkCred>();
  /** Job UUIDs with indices corresponding to job IDs. */
  private ArrayList<UUID> jobs = new ArrayList<UUID>();

  /** Used to hold session connections for reuse. */
  public transient SessionCache sessions = new SessionCache();

  /** Basic user login cookie. */
  public static class Cookie {
    public String email;
    public String hash;
    public String password;
    public String authToken;
    private transient Server server;

    protected Cookie() { }

    public Cookie(Server server) { this.server = server; }

    /** Can be overridden by subclasses. */
    public Server server() { return server; }

    /** Attempt to log in with the given information. */
    public User login() {
      if (email != null && authToken != null) {
        User user = server().users.get(User.normalizeEmail(email));
        return user;
      } 
      if (email == null || (email = email.trim()).isEmpty())
        throw new RuntimeException("No email address provided.");
      if (hash == null && (password == null || password.isEmpty()))
        throw new RuntimeException("No password provided.");
      User user = server().users.get(User.normalizeEmail(email));
      if (user == null)
        throw new RuntimeException("Invalid username or password.");
      if (hash == null)
        hash = user.hash(password);
      if (!hash.equals(user.hash))
        throw new RuntimeException("Invalid username or password.");
      if (!user.validated)
        throw new RuntimeException("This account has not been validated.");
      return user;
    }
  }

  // A job owned by this user.
  private class UserJob extends Job {
    public User user() { return User.this; }
    public Server server() { return User.this.server(); }
  }

  /** The minimum allowed password length. */
  public static final int PASS_LEN = 6;

  /** Create an anonymous user. */
  public User() { }

  /** Create a user with the given email and password. */
  public User(String email, String password) {
    this.email = email;
    setPassword(password);
  }

  /** Get the server this user belongs to. */
  public abstract Server server();

  /** Check if the given password is correct for this user. */
  public synchronized boolean checkPassword(String password) {
    return hash(password).equals(hash);
  }

  /** Set the password for this user. Checks password length and hashes. */
  public synchronized void setPassword(String pass) {
    if (pass == null || pass.isEmpty())
      throw new RuntimeException("No password was provided.");
    if (pass.length() < PASS_LEN)
      throw new RuntimeException("Password must be "+PASS_LEN+"+ characters.");
    salt = salt();
    hash = hash(pass);
  }

  /** Get an object containing information to return on login. */
  public Cookie getLoginCookie() {
    Cookie cookie = new Cookie(server());
    cookie.email = email;
    cookie.hash = hash;
    return cookie;
  }

  /** Add a URL to a user's history. */
  public synchronized void addHistory(URI u) {
    if (!isAnonymous() && Config.global.max_history > 0) try {
      history.remove(u);
      while (history.size() > Config.global.max_history)
        history.removeLast();
      history.addFirst(u);
    } catch (Exception e) {
      // Just don't add it.
    }
  }

  /** Check if a user is anonymous. */
  public boolean isAnonymous() { return email == null; }

  /** Normalize an email string for comparison. */
  public static String normalizeEmail(String email) {
    if (email == null)
      return null;
    String[] parts = email.split("@");
    if (parts.length != 2)
      throw new RuntimeException("Invalid email address.");
    return parts[0].toLowerCase()+"@"+IDN.toASCII(parts[1]).toLowerCase();
  }

  /** Get the normalized email address of this user. */
  public String normalizedEmail() {
    return normalizeEmail(email);
  }

  /** Save a {@link Job} to this {@code User}'s {@code jobs} list. */
  public synchronized Job saveJob(Job job) {
    job.owner = normalizedEmail();
    jobs.add(job.uuid());
    job.jobId(jobs.size());
    return job;
  }

  /** Get one of this user's jobs by its ID. */
  public synchronized Job getJob(int id) {
    try {
      UUID uuid = jobs.get(id);
      return server().findJob(uuid);
    } catch (Exception e) {
      throw new RuntimeException("No job with that ID.", e);
    }
  }

  /** Get a list of actual jobs owned by the user. */
  public synchronized List<Job> jobs() {
    // FIXME: Inefficient...
    List<Job> list = new LinkedList<Job>();
    for (int i = 0; i < jobs.size(); i++) try {
      list.add(getJob(i));
    } catch (Exception e) {
      // This handles invalid UUIDs in the jobs list.
    } return list;
  }

  /** Generate a random salt using a secure random number generator. */
  public static String salt() { return salt(24); }

  /** Generate a random salt using a secure random number generator. */
  public static String salt(int len) {
    byte[] b = new byte[len];
    SecureRandom random = new SecureRandom();
    random.nextBytes(b);
    return StorkUtil.formatBytes(b, "%02x");
  }

  /** Hash a password with this user's salt. */
  public String hash(String pass) {
    return hash(pass, salt);
  }

  /** Hash a password with the given salt. */
  public static String hash(String pass, String salt) {
    try {
      String saltpass = salt+'\n'+pass;
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = saltpass.getBytes("UTF-8");

      // Run the digest for three rounds.
      for (int i = 0; i < 3; i++)
        digest = md.digest(digest);

      return StorkUtil.formatBytes(digest, "%02x");
    } catch (Exception e) {
      throw new RuntimeException("Couldn't hash password.");
    }
  }

  /** ZL: TODO: token generator */
  public static String tokenGenerator(String email, String time, String salt) {
    try {
      String saltpass = email+'\n'+time+'\n'+salt;
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = saltpass.getBytes("UTF-8");

      // Run the digest for three rounds.
      for (int i = 0; i < 3; i++)
        digest = md.digest(digest);

      return StorkUtil.formatBytes(digest, "%02x");
    } catch (Exception e) {
      throw new RuntimeException("Couldn't generate a token for reset password.");
    }
  }

  /** Add a credential for this user, returning a UUID. */
  public synchronized String addCredential(StorkCred cred) {
    UUID uuid = UUID.randomUUID();
    credentials.put(uuid, cred);
    return uuid.toString();
  }

  /** Get a simplified list of this user's credentials. */
  public synchronized Map<UUID,Object> credentialList() {
    Map<UUID,Object> map = new HashMap<UUID,Object>();
    for (Map.Entry<UUID,StorkCred> e : credentials.entrySet())
      map.put(e.getKey(), e.getValue().getInfo());
    return map;
  }

  /** Validate a user given a token. */
  public synchronized boolean validate(String token) {
    if (validated)
      return true;
    if (token == null || validationToken == null)
      return false;
    if (!token.equals(validationToken))
      return false;
    validationToken = null;
    return validated = true;
  }

  /** Get or create a validation token for this user. */
  public synchronized String validationToken() {
    if (validated)
      throw new RuntimeException("User is already validated.");
    if (validationToken == null)
      validationToken = salt(12);
    return validationToken;
  }

  /** ZL: for reset password */
  public synchronized String authToken() {
    if (authToken == null) {
      registerMoment = now();
      authToken = tokenGenerator(email, String.valueOf(registerMoment), salt(10));
    }
    return authToken;
  }

  /** Call this to send the user a validation mail. */
  public synchronized Bell<?> sendValidationMail() {
    String base = "https://storkcloud.org/api/stork/user";
    //String token = validationToken();
   //server().cacheToken(token, )
    final String url = String.format(
      base+"?action=validate&user=%s&token=%s",
      normalizedEmail(), validationToken());
    return new Mail() {{
      from = Config.global.email;
      to = User.this.email;
      subject = "Complete your registration with StorkCloud";
      body =
        "Thank you for registering with StorkCloud!\n\n"+
        "Please go here to complete your registration:\n\n"+url;
    }}.send();
  }

  public synchronized String exist(){
    if (User.this.email == null) throw new RuntimeException("no user exist");
    return User.this.email;
  }
   
  /** ZL: TODO: call to send user mail to reset password. */
  public synchronized Bell<?> sendResetPasswordMail() {
    String base = "https://storkcloud.org/api/stork/user";
    String authToken = authToken();
    server().cacheToken(authToken, normalizedEmail());
    final String url = String.format(
      base+"?action=resetPassword&user=%s&authToken=%s",
      normalizedEmail(), authToken);
    return new Mail() {{
      from = Config.global.email;
      to = User.this.email;
      subject = "Reset your stork password";
      body =
        "Hi\n\n"+
        "Welcome to reset your password.\n\n"+
        "Please click: "+url+"\n\n"+
        "Sincerely,\n"+
        "Stork";
    }}.send();
  }

  /** ZL: creating a big string composed of multiple receipients */
  public synchronized Bell<?> sendAdminMail() {
     int i=0;
     server().mailList="";
     for(String mail: server().administrators){
       server().mailList += mail + " ";
       stork.util.Log.info("mailList is creating: ",server().mailList);
     }
     return new Mail() {{
      from = Config.global.email;
      to = server().mailList; 
      subject = "New user registered stork";
      body = 
         "Hi Admin,\n\n"+
         User.this.email+" become a user of stork.\n\n"+
         "Sincerely,\n"+
         "Stork";          
     }}.send();
  }

  /**
   * This is thrown when a user is trying to perform an action but is not
   * validated.
   */
  public static class NotValidatedException extends RuntimeException {
    public NotValidatedException() {
      super("This account has not been validated.");
    }
  }
}
