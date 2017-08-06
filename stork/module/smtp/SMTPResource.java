package stork.module.smtp;

import io.netty.buffer.*;

import stork.feather.*;
import stork.module.*;
import stork.scheduler.*;

public class SMTPResource extends Resource<SMTPSession, SMTPResource>{
  SMTPResource(SMTPSession session, Path path) {
    super(session,path);
  }

  public Bell<SMTPResource> mkdir() {
    return new Bell<SMTPResource>(this);
  }

  public <D extends Resource<?,D>> Transfer<SMTPResource,D> transferTo(D resource) {
    throw new UnsupportedOperationException();
  }

  public Sink<SMTPResource> sink() {
    return new SMTPSink(this);
  }

  public Bell onTransferComplete() {
    return null;
  }
}
