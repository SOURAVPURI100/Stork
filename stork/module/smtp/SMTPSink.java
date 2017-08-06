package stork.module.smtp;

import stork.feather.*;
import stork.feather.util.*;
import io.netty.buffer.*;

import java.net.*;
import java.io.*;
import java.util.*;

public class SMTPSink extends Sink<SMTPResource>{ 

  SMTPSink(SMTPResource resource) {
    super(resource);
  }

  private SMTPChannel channel() {
    return destination().session.channel;
  }

  protected Bell start() {
    Bell bell = destination().initialize();
    channel().send("--separate");
    String fileName = source().path.name();
    String mimeType = MimeTypeMap.forFile(fileName);
    if(mimeType == null)  channel().send("Content-Type: application/octet-stream");
    else channel().send("Content-Type: "+mimeType);
    channel().send("Content-Transfer-Encoding: base64");
    channel().send("Content-Disposition: attachment; filename="+fileName);
    channel().send("");
    channel().enableBase64();
    return bell;
  }

  //drain message body
  protected Bell drain(final Slice slice) {
    channel().send(slice.asByteBuf());
    return null;
  }

  //footers
  protected void finish(Throwable t) {
    channel().disableBase64();
    channel().send("\n--separate--");
  }
}
