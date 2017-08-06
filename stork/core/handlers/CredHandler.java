package stork.core.handlers;

import java.util.*;

import stork.core.server.*;
import stork.cred.*;

/** Handles creating credentials. */
public class CredHandler extends Handler<ActionCredRequest> {
  public void handle(ActionCredRequest req) {
    req.assertLoggedIn();

    if (req.action == null) {
      throw new RuntimeException("No action specified.");
    } if (req.action.equals("list")) {
      if (req.user().credentials == null)
        req.ring(Collections.emptySet());
      else
        req.ring(req.user().credentialList());
    } else if (req.action.equals("create")) {
      req.assertMayChangeState();
      StorkCred<?> cred = req.resolve();
      final String _uuid = req.user().addCredential(cred);
      req.ring(new Object() { String uuid = _uuid; });
      server.dumpState();
    } else {
      throw new RuntimeException("Invalid action.");
    }
  }
}

/** A CredRequest with an extra action field. */
class ActionCredRequest extends CredRequest {
  String action;
}
