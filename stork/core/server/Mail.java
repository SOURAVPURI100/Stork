package stork.core.server;

import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;

import stork.core.*;
import stork.feather.Bell;
import stork.feather.util.*;
import stork.util.*;

/** A class for composing and sending email. */
public class Mail {
  public String from, to, subject, body;

  /** Asynchronously send the message. */
  public Bell<?> send() {
    return new ThreadBell<Void>() {
      public Void run() {
        Mail.this.run();
        return null;
      }
    }.start();
  }

  /** Send the message. */
  public void run() {
    /** ZL: create receipients list */
    String[] mails = to.split("\\s+");
    InternetAddress[] mailList = new InternetAddress[mails.length];
    try{
      int i=0;
      for(String mail: mails){
       mailList[i++] = new InternetAddress(mail);
      }
    } catch (Exception e) {
       //TODO: hopefully no exception here
    }
    String smtp = Config.global.smtp_server;
    if (smtp == null)
      throw new Error("SMTP is not configured.");

    Properties prop = System.getProperties();
    Session session = Session.getDefaultInstance(prop);
    prop.setProperty("mail.smtp.host", smtp);

    try {
      MimeMessage msg = new MimeMessage(session);
      msg.setFrom(new InternetAddress(from));
      /**ZL: able to be sent to multiple receipients */
      msg.addRecipients(Message.RecipientType.TO, mailList);
      msg.setSubject(subject);
      msg.setText(body);

      Log.info("Sending mail to: ", to);
      Transport.send(msg);
      Log.info("Mail send successfuly to: ", to);
    } catch (Exception e) {
      Log.info("Failed sending mail to: ", to);
      throw new RuntimeException(e);
    }
  }
}
