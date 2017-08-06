package stork.module.smtp;

import io.netty.buffer.*;

import stork.feather.*;
import stork.module.*;
import stork.scheduler.*;

public class SMTPSession extends Session<SMTPSession,SMTPResource> {
  SMTPChannel channel;

  public SMTPSession(URI uri) {
    super(uri);
  }

  protected void cleanup() {
    channel.close();
  }

  protected Bell<SMTPSession> initialize() {
    Bell bell = new Bell();
    channel = new SMTPChannel(bell);
    channel.sendCommand("HELO buffalo.edu");
    channel.sendCommand("MAIL FROM:<jerryant@buffalo.edu>");
    channel.sendCommand("RCPT TO:<jerryant@buffalo.edu>");
    channel.sendCommand("DATA");
    channel.send("Subject:-Delivery from Stork-");
    channel.send("MIME-Version: 1.0");
    channel.send("Content-Type: multipart/mixed; boundary=separate");
    channel.send("");
    channel.send("--separate");
    channel.send("Content-Type: text/html");
    channel.send("Delivery from Stork");
    return bell.as(this);
  }

  public SMTPResource select(Path path) {
    return new SMTPResource(this,path);
  }
}
