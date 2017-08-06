package stork.module.http;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;

/**
 * Convenient HTTP initializer for handler setting up.
 */
public class HTTPInitializer extends ChannelInitializer<SocketChannel> {

  private boolean ssl;
  private HTTPBuilder builder;

  public HTTPInitializer (String scheme, HTTPBuilder builder) throws HTTPException {
    ssl = false;
    if (scheme == null) {
      throw new HTTPException("Error: null http scheme");
    } else if (scheme.equalsIgnoreCase("https")) {
      ssl = true;
    }

    this.builder = builder;
  }

  /**
   * Adds pipelines to channel.
   * 
   *  @param ch channel to be operated on
   */
  protected void initChannel(SocketChannel ch) throws Exception {
    ChannelPipeline pipe = ch.pipeline();

    if (ssl) {
      // HTTPs connection
      SSLEngine sslEng = getSsl(null);
      sslEng.setUseClientMode(true);
      pipe.addLast("SSL", new SslHandler(sslEng, false));
    }

    pipe.addFirst("Timer", new ReadTimeoutHandler(30));
    pipe.addLast("Codec", new HttpClientCodec());
    pipe.addLast("Inflater", new HttpContentDecompressor());
    pipe.addLast("Handler", new HTTPMessageHandler(builder));
  }

  // HTTPS transmission
  private SSLEngine getSsl(String proto) throws NoSuchAlgorithmException {
    String protocol = (proto == null) ? "TLS" : proto;
    SSLContext context = SSLContext.getInstance(protocol);
    try {
      context.init(null, null, null);
    } catch (KeyManagementException e) {
      System.err.println(e.getMessage());
    }

    return context.createSSLEngine();
  }
}
