package stork.module.smtp;

import stork.feather.*;
import stork.feather.util.*;
import stork.module.*;
import stork.module.ftp.*;

public class SMTPModule extends Module<SMTPResource> {
  {
    name("Stork SMTP Module");
    protocols("mailto");
    description("A module interacting with SMTP systems.");
  }

  public SMTPResource select(URI uri, Credential credential) {
    return new SMTPSession(uri).root();
  }
}
