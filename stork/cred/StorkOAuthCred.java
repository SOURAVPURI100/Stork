package stork.cred;

import stork.ad.*;
import stork.feather.*;

public class StorkOAuthCred extends StorkCred<String> {
  public String token;

  public StorkOAuthCred(String token) {
    super("oauth");
    this.token = token;
  }

  public String data() {
    return token;
  }

  protected Object[] hashables() {
    return new Object[] { token };
  }
}
