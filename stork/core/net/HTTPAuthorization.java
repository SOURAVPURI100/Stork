package stork.core.net;

import java.nio.charset.*;

import io.netty.buffer.*;
import io.netty.handler.codec.base64.*;

import stork.cred.*;

/**
 * This represents a username and password (or bearer token) used to
 * authenticate with Stork.
 */
public class HTTPAuthorization {
  public String username, password, bearer;

  /** Parse an Authorization header. */
  public HTTPAuthorization(String credentials) {
    String[] parts = credentials.split(" ");
    if (parts.length != 2)
      throw new IllegalArgumentException("Bad credential format");
    if ("Basic".equals(parts[0]))
      parseBasic(parts[1]);
    else if ("Bearer".equals(parts[0]))
      parseBearer(parts[1]);
    else
      throw new IllegalArgumentException("Unsupported credential type");
  }

  /** Parse an Authorization header. Return null on error. */
  public static HTTPAuthorization parse(String credentials) {
    try {
      return new HTTPAuthorization(credentials);
    } catch (Exception e) {
      return null;
    }
  }

  /** Token is a Base64 encoded "username:password" string. */
  private void parseBasic(String token) {
    ByteBuf enc = Unpooled.wrappedBuffer(token.getBytes());
    ByteBuf dec = Base64.decode(enc);
    String joined = dec.toString(StandardCharsets.UTF_8);
    String[] split = StorkUserinfo.split(joined);
    username = split[0];
    password = split[1];
  }

  /** Token is a Base64 encoded bearer token. */
  private void parseBearer(String token) {
    ByteBuf enc = Unpooled.wrappedBuffer(token.getBytes());
    ByteBuf dec = Base64.decode(enc);
    token = dec.toString(StandardCharsets.UTF_8);
  }
}
