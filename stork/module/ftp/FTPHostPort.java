package stork.module.ftp;

import java.net.*;
import java.util.*;
import java.io.*;

import stork.util.*;

// A utility for parsing PASV and EPSV replies.

public class FTPHostPort {
  public byte[] bytes;  // Only the first four bytes of this are used.
  public int port;

  public FTPHostPort(FTPChannel.Reply reply) {
    this(reply.message().split("[()]")[1]);
  }

  public FTPHostPort(String csv) {
    try {
      bytes = new byte[6];
      int i = 0;
      for (String s : csv.split(","))
        bytes[i++] = (byte) Short.parseShort(s);
      if (i != 6)
        throw null;
      port = ((bytes[4]&0xFF)<<8) + (bytes[5]&0xFF);
    } catch (Exception e) {
      throw new RuntimeException("Malformed PASV reply.", e);
    }
  }

  // Get the host/port as a socket address.
  public SocketAddress getAddr() {
    try {
      byte[] b = { bytes[0], bytes[1], bytes[2], bytes[3] };
      InetAddress ia = InetAddress.getByAddress(b);
      return new InetSocketAddress(ia, port & 0xFFFF);
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }

  // Get the IP as a string.
  public String getHost() {
    return (bytes[0]&0xFF)+"."+(bytes[1]&0xFF)+"."+
           (bytes[2]&0xFF)+"."+(bytes[3]&0xFF);
  }

  // Get the port.
  public int getPort() {
    return port & 0xFFFF;
  }

  // Return a comma-separated byte representation.
  public String toString() {
    return (bytes[0]&0xFF)+","+(bytes[1]&0xFF)+
      ","+(bytes[2]&0xFF)+","+(bytes[3]&0xFF)+
      ","+((port&0xFF00)>>8)+","+(port&0xFF);
  }

  private void subnetHack(byte[] b) {
    // Make sure the first three octets are the same as the control channel IP.
    // If they're different, assume the server is a LIAR. We should try
    // connecting to the control channel IP. If only the last octet is
    // different, then don't worry, it probably knows what it's talking about.
    // This is to fix issues with servers telling us their local IPs and then
    // us trying to connect to it and waiting forever. This is just a hack and
    // should be replaced with something more accurate, or, better yet, test if
    // we can act as a passive mode client and have them connect to us, since
    // that would be better and assumably we have control over that.
    if (b[0] == bytes[0])
    if (b[1] == bytes[1])
    if (b[2] == bytes[2])
      return;
    bytes = b;
    Log.fine("Adjusting server IP to: ", getHost());
  }
}
