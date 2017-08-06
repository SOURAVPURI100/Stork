package stork.module.ftp;

// A utility class for translating standard FTP reply codes into human-readable
// messages.

public final class FTPMessages {
  // Get a human readable string describing the reply code's purpose.
  public static String fromCode(int code) {
    if (code >= 100 || code < 700) switch (code) {
      case 100: return "The requested action has been initated.";
      case 110: return "Restart marker";
      case 120: return "The service will be ready shortly.";
      case 125: return "Data connection is already open.";
      case 150: return "About to open data channel connection.";

      case 200: return "The requested action has completed.";
      case 202: return "Command not implemented; unnecessary.";
      case 211: return "System information reply.";
      case 212: return "Directory status reply.";
      case 213: return "File status reply.";
      case 214: return "Command usage reply.";
      case 215: return "System type reply.";
      case 220: return "The service is ready.";
      case 221: return "The control connection has been closed.";
      case 225: return "Data connection is open.";
      case 226: return "The data channel has been closed.";
      case 227: return "Passive mode reply.";
      case 228: return "Long passive mode reply.";
      case 229: return "Extended passive mode reply.";
      case 230: return "User has logged in.";
      case 231: return "User has logged out.";
      case 232: return "Use logout pending transfer completion.";
      case 250: return "Requested file action completed.";
      case 257: return "The requested path has been created.";

      case 300: return "The requested action requires more information.";
      case 331: return "Password required for user.";
      case 332: return "Account required for login.";
      case 350: return "Requested file action requires more information.";

      case 400: return "Request rejected due to transient condition.";
      case 421: return "Service not available; closing control channel.";
      case 425: return "Data channel cannot be opened.";
      case 426: return "Connection closed; transfer aborted.";
      case 430: return "Invalid username or password.";
      case 434: return "Requested host unavailable.";
      case 450: return "Requested file action rejected.";
      case 451: return "Requested action aborted due to error.";
      case 452: return "Insufficent storage or resource is unavailable.";

      case 500: return "Request rejected due to client error.";
      case 501: return "Syntax error in command argument.";
      case 502: return "Command not implemented.";
      case 503: return "Bad command sequence.";
      case 504: return "Command not implemented for given parameter.";
      case 530: return "Not logged in.";
      case 532: return "Need account for storing files.";
      case 550: return "Requested file resource is unavailable.";
      case 551: return "Requested action aborted; unknown page type.";
      case 552: return "Insufficient storage.";
      case 553: return "Invalid file name.";

      case 600: return "Protected reply (unknown type).";
      case 631: return "Integrity protected reply.";
      case 632: return "Confidental and integrity protected reply.";
      case 633: return "Confidental protected reply.";

      default:  // None found, try a more general message.
        if (code%10  > 0) return fromCode(code/10*10);    // Try xy0.
        if (code%100 > 0) return fromCode(code/100*100);  // Try x00.
    } return "Unknown server reply.";
  }
}
