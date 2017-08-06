package stork.core.handlers;

import java.util.*;

import stork.core.server.*;
import stork.core.*;

/** Send information about the server. */
public class InfoHandler extends Handler<InfoRequest> {
  // Send transfer module information.
  void sendModuleInfo(InfoRequest req) {
    req.ring(req.server.modules.modulesByHandle());
  }

  // Send server information. But for now, don't send anything until we
  // know what sort of information is good to send.
  void sendServerInfo(final InfoRequest req) {
    req.ring(new Object() {
      String version = stork.core.Main.version();
      Set<String> commands = req.server.handlers.keySet();
    });
  }

  // Send information about a credential or about all credentials.
  void sendCredInfo(InfoRequest req) {
    req.assertLoggedIn();
    if (req.uuid != null) try {
      req.ring(req.user().credentials.get(req.uuid));
    } catch (Exception e) {
      throw new RuntimeException("No credential with that ID.");
    } else {
      req.ring(req.user().credentials);
    }
  }

  public void handle(InfoRequest req) {
    if (req.action.equals("module"))
      sendModuleInfo(req);
    else if (req.action.equals("server"))
      sendServerInfo(req);
    else if (req.action.equals("cred"))
      sendCredInfo(req);
    else
      throw new RuntimeException("Invalid action.");
  }
}

class InfoRequest extends Request {
  String action = "module";
  String uuid;  // Used for cred info.
}
