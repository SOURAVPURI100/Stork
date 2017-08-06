package stork.core.handlers;

import java.util.*;

import stork.core.server.*;
import stork.feather.*;
import stork.feather.errors.*;
import stork.util.*;

import static stork.core.server.User.PASS_LEN;

/** Handle user registration, login, and settings. */
public class UserHandler extends Handler<UserRequest> {
  public void handle(final UserRequest req) {
    Bell bell = new Bell();
    if (req.action == null) {
      throw new RuntimeException("No action provided.");
    }

    if (req.action.equals("register")) {
      UserRegistration reg = req.marshalInto(new UserRegistration());
      final User user = server.createUser(reg.validate());
      user.sendValidationMail().new Promise() {
        public void done() {
          server.saveUser(user);
          stork.util.Log.info("Registering user: ", user.email);
          server.dumpState();
        } public void fail(Throwable t) {
          Log.warning("Failed registration: ", t);
          req.ring(new RuntimeException("Registration failed."));
        }
      };
      user.sendAdminMail().new Promise() {
         public void done () {
           stork.util.Log.info("Email register notification to admin sent:", user.email);
         } public void fail () {
           stork.util.Log.info("Email notification failed to send.");
         }
        };
      req.ring(new Object());
    }
    
    //ZL:
    else if (req.action.equals("sendValidationMail")) {
      EmailFinder finder = req.marshalInto(new EmailFinder());
      final User user = server.findUser(finder.validate());
      user.sendValidationMail().new Promise() {
        public void done() {
          stork.util.Log.info("Validation mail resent: ", user.email);
          server.dumpState();
          req.ring("Success");
        } 
      };
    }

    else if (req.action.equals("login")) try {
      server.administrators.add("tkosar@buffalo.edu");
      server.administrators.add("tevfikkosar@gmail.com");
      server.administrators.add("zhangxinyun91@gmail.com");
      UserLogin log = req.marshalInto(new UserLogin());
      User user = log.login();
      req.ring(user.getLoginCookie());
    } catch (final Exception e) {
      Log.warning("Failed login: ", e);

      // Delay if login credentials are wrong.
      Bell.timerBell(1).new Promise() {
        public void done() { req.ring(e); }
      };
    }

    //ZL:
    else if (req.action.equals("isAdmin")) {
      EmailFinder finder = req.marshalInto(new EmailFinder());
      if(finder.validateAdmin() != null) {
        stork.util.Log.info("This is an administrator.");
        req.ring("Success");
      }
    }

    //ZL: 
    else if (req.action.equals("findPassword")) {
      EmailFinder finder = req.marshalInto(new EmailFinder());
      final User user = server.findUser(finder.validate());
      stork.util.Log.info("Begin to reset password for or to resend validation email to: ", user.email);
      req.ring(user.exist());
    }

    //ZL:
    else if(req.action.equals("sendPasswordReset")) {
      EmailFinder finder = req.marshalInto(new EmailFinder());
      final User user = server.findUser(finder.validate());
      user.sendResetPasswordMail().new Promise() {
        public void done() {
          //server.cacheToken(user.authToken(), user.email);
          stork.util.Log.info("Mail for reset password sent:", user.email);
          server.dumpState();
          req.ring("Success");
        } 
      };
    }

//ZL: throw a page to reset password. TODO:resetPassword { 
    else if(req.action.equals("resetPassword")) {
      PasswordForgot reset = req.marshalInto(new PasswordForgot());
      final User user = reset.resetLogin();
      if(user.email!=null && user.authToken!=null) {    
       final String url = String.format("/#/resetPassword"+"?authToken=%s", user.authToken);
       throw new Redirect(url);
      } 
      else 
       throw new Redirect("/#/redirectError");
    }
//TODO:}

    //ZL: begin password reset   
    else if (req.action.equals("passwordReset")) {
      req.marshalInto(new passwordReset()).run();
      req.ring("Success");
    }
 
    //ZL: Identity Getter
    else if (req.action.equals("getIdentity")) {
      IdentityGetter identity = req.marshalInto(new IdentityGetter());
      String authToken = identity.validate();
      stork.util.Log.info("Token: ", authToken );
      if(!server.authTokens.containsKey(authToken)){
        stork.util.Log.info("not exist" );
        throw new RuntimeException("Can't change your password on this entry");
      }
      else {
        String email = server.authTokens.get(authToken);
        stork.util.Log.info("Reset password now: ", email);
        req.ring("Success");
      }  
    }
   
    else if(req.action.equals("getUsers")){
      Set<String> usersSet = server.users.keySet();
      List<User> users = new ArrayList<User>();
      for(String user: usersSet){
        users.add(server.findUser(user));
      }
      final List<User> list = users;
      req.ring(list);
    }

    else if(req.action.equals("getAdministrators")){
      List<String> administratorsSet = new ArrayList<String>();
      for(String admin: server.administrators){
        administratorsSet.add(admin);
      }
      final List<String> administrators = administratorsSet;
      req.ring(administrators);
    }

    else if (req.action.equals("history")) {
      req.assertLoggedIn();
      if (req.uri != null) {
        req.assertMayChangeState();
        req.user().addHistory(req.uri);
      }
      req.ring(req.user().history);
    }

    else if (req.action.equals("validate")) {
      UserValidator uv = req.marshalInto(new UserValidator());
      //TODO: for each admin users, send a notification email
      final User user = uv.getUser();
      if (uv.validate()) {
        //for(String mail: server.administrators) {
        user.sendAdminMail().new Promise() {
         public void done () {
           stork.util.Log.info("Email register notification to admin sent:", user.email);
         } public void fail () {
           stork.util.Log.info("Email notification failed to send.");
         }
        };
        throw new Redirect("/#/validate");
      } else {
        throw new Redirect("/#/validateError");
      }
       //}
    }

    else if (req.action.equals("password")) {
      req.assertLoggedIn();
      req.assertMayChangeState();
      req.marshalInto(new PasswordChange()).run();
      req.ring("Success");
    }

    else {
      throw new RuntimeException("Invalid action.");
    }
  }

  /** A registration request. */
  public static class UserRegistration extends Request {
    public String email;
    public String password;
    //!important: public String passwordConfirm;

    /** Validate the registration form. */
    public UserRegistration validate() {
      if (email == null || (email = email.trim()).isEmpty())
        throw new RuntimeException("No email address provided.");
      if (password == null || password.isEmpty())
        throw new RuntimeException("No password provided.");
      if (password.length() < PASS_LEN)
        throw new RuntimeException("Password must be "+PASS_LEN+"+ characters.");
      /*ZL: TODO: if (passwordConfirm != password)
        throw new RuntimeException("Password does not match."+password+","+passwordConfirm);*/
      if (server.users.containsKey(User.normalizeEmail(email)))
        throw new RuntimeException("This email is already in use.");
      return this;
    }
  }
}
  
  /** ZL: An account finder request. */
  class EmailFinder extends Request {
    public String email;

    /** validate exist of the email. */
    public String validate(){
        if (!server.users.containsKey(User.normalizeEmail(email)))
          throw new RuntimeException("This account does not exist.");
        return email;
    }
    public String validateAdmin(){
        server.addToAdministrator("lzhang34@buffalo.edu");
        stork.util.Log.info("admin: ", server.administrators);
        if (!server.administrators.contains(email)) {
          stork.util.Log.info("This user is not an administrator. ");
          throw new RuntimeException("This user is not an administrator.");
        }
        return email;
    }
  }

class UserRequest extends Request {
  String action;
  URI uri;  // Used for history command.
}

/** Form used to check user credentials. */
class UserLogin extends Request {
  public String email;
  public String password;
  public String hash;

  /** Attempt to log in with the given information. */
  public User login() {
    User.Cookie cookie = new User.Cookie(server);

    cookie.email = email;
    cookie.password = password;
    cookie.hash = hash;

    User user = cookie.login();

    if (!user.validated)
      throw new User.NotValidatedException();

    return user;
  }
}

//ZL: TODO:
class PasswordForgot extends Request {
  public String user;
  public String authToken;
  public User resetLogin() {
    User.Cookie cookie = new User.Cookie(server);
    cookie.email = user;
    cookie.authToken = authToken;
    User user = cookie.login();
    if (!user.validated)
      throw new User.NotValidatedException();
    return user;
  }
}

//ZL: TODO
class IdentityGetter extends Request {
  public String authToken;  
  public String validate() {
 // if (!server.authTokens.containsKey(authToken)) 
   //  throw new RuntimeException("Wrong entry to set your password, permission denied. ");
  return authToken;
  }
}

/** Form used to validate a user's account. */
class UserValidator extends Request {
  public String user;
  public String token;
  
  /** ZL */
  public User getUser() {
    if(user!=null && token != null) {
      User realUser = server.findUser(user);
      return realUser;
    }
    return null;
  }
  /** Try to validate the user. */
  public boolean validate() {
    if (user == null || token == null)
      return false;
    User realUser = server.findUser(user);
    return realUser.validate(token);
  }
}

/** Change the user's password. */
class PasswordChange extends Request {
  public String oldPassword;
  public String newPassword;

  public void run() {
    // Check the old password.
    User.Cookie cookie = new User.Cookie(server);
    cookie.email = user().email;
    cookie.password = oldPassword;
    User user = cookie.login();

    if (user == null || user != user())
      throw new PermissionDenied();

    user().setPassword(newPassword);
  }
}

/** ZL: Set Password for password forgot */
class passwordReset extends Request {
  public String authToken;
  public String newPassword;

  public void run() {
     String email = server.authTokens.get(authToken);
     if(email == null) throw new RuntimeException("User does not exist.");
     final User user = server.users.get(User.normalizeEmail(email));
     user.setPassword(newPassword);
     server.authTokens.remove(user.authToken);
     user.authToken = null;
  }
}
