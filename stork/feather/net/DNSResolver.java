package stork.feather.net;

import java.net.*;

import stork.feather.*;
import stork.feather.util.*;

/**
 * An asychronous DNS resolver.
 */
public class DNSResolver {
  /** Resolve the host address into an array of Internet addresses. */
  public static Bell<InetAddress[]> resolveAll(final String host) {
    // TODO: Replace this with a real asynchronous lookup.
    return new ThreadBell<InetAddress[]>() {
      public InetAddress[] run() throws UnknownHostException {
        return InetAddress.getAllByName(host);
      }
    }.start();
  }

  /** Resolve the host address into an Internet address. */
  public static Bell<InetAddress> resolve(final String host) {
    return resolveAll(host).new As<InetAddress>() {
      public InetAddress convert(InetAddress[] addrs) {
        return addrs[0];
      }
    };
  }
}
