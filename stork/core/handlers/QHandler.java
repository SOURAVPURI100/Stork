package stork.core.handlers;

import java.util.*;

import stork.ad.*;
import stork.core.server.*;
import stork.scheduler.*;

public class QHandler extends Handler<QRequest> {
  public void handle(QRequest req) {
    req.assertLoggedIn();
    final List list = req.user().jobs();
    req.ring(!req.count ? list : new Object() {
      int count = list.size();
    });
  }
}

class QRequest extends Request {
  boolean count = false;
}
