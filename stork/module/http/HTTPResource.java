package stork.module.http;

import java.util.*;

import stork.feather.*;

/**
 * Stores the requested full {@link Path}, and state information of the 
 * connection. It creates {@link HTTPTap} instances.
 */
public class HTTPResource extends Resource<HTTPSession, HTTPResource> {

  // Rung when the first resource response header is received
  private Bell<Stat> statBell = new Bell<Stat>();

  /**
   * Constructs a {@code resource} with HTTP connection request.
   * 
   * @param session the class where this request made from
   * @param path requested resource {@code path}
   */
  protected HTTPResource(HTTPSession session, Path path) {
    super(session, path);
  }

  public HTTPTap tap() {
    return new HTTPTap();
  }

  public synchronized Bell<Stat> stat() {
    return initialize().new AsBell<Stat>() {
      public Bell<Stat> convert(HTTPResource r) {
        if (statBell.isDone())
          return statBell;

        HTTPBuilder builder = session.builder;

        // We need to make a HEAD request.
        if (!builder.onCloseBell.isDone()) {
          HTTPChannel ch = builder.getChannel();

          ch.addChannelTask(new HTTPTap());  // FIXME hacky
          ch.writeAndFlush(builder.prepareHead(path.toString()));
        } else {
          statBell.ring(new HTTPException("Http session " +
                builder.getHost() + " has been closed."));
        }

        return statBell;
      }
    }.new AsBell<Stat>() {
      // Fetch the page to do listing if necessary.
      public Bell<Stat> convert(final Stat stat) {
        if (!stat.dir)
          return Bell.wrap(stat);
        Bell<List<Stat>> listBell =
          new HTTPListParser(uri(), tap()).getListing();
        return listBell.new As<Stat>() {
          public Stat convert(List<Stat> set) {
            return stat.setFiles(set);
          }
        };
      }
    };
  }

  /**
   * This can be considered as a specific download task for the
   * request from a {@link HTTPResource}.
   */
  public class HTTPTap extends Tap<HTTPResource> {

    protected Bell<Void> onStartBell, sinkReadyBell;
    private HTTPBuilder builder;
    private String resourcePath;

    /**
     * Constructs a {@code tap} associated with a {@code resource}
     * that receives data from HTTP connection.
     */
    public HTTPTap() {
      super(HTTPResource.this);
      this.builder = HTTPResource.this.session.builder;
      onStartBell = new Bell<Void> ();
      setPath(path.toString());
    }

    public Bell<?> start(final Bell bell) {
      return initialize().and(bell).new AsBell() {
        public Bell convert(Object o) {
          if (builder.onCloseBell.isDone()) {
            return onStartBell.cancel();
          }
          sinkReadyBell = bell;

          synchronized (builder.getChannel()) {
            if (!builder.onCloseBell.isDone()) {
              HTTPChannel ch = builder.getChannel();

              if (builder.isKeepAlive()) {
                ch.addChannelTask(HTTPTap.this);
                ch.writeAndFlush(
                    builder.prepareGet(resourcePath));
              } else {
                builder.tryResetConnection(HTTPTap.this);
              }
            } else {
              onStartBell.ring(new HTTPException("HTTP session " +
                    builder.getHost() + " has been closed."));
              session.close();
            }
          }

          sinkReadyBell.new Promise() {
            public void fail(Throwable t) {
              onStartBell.ring(t);
              finish(t);
            }
          };

          return onStartBell;
        }
      };
    }

    public Bell<?> drain(Slice slice) {
      return super.drain(slice);
    }

    public void finish(Throwable t) { super.finish(t); }

    /** 
     * Tells whether this {@code HTTPTap} instance has acquired
     * state info.
     */
    protected boolean hasStat() {
      return statBell.isDone();
    }

    /** Sets state info and rings its {@code state Bell}. */
    protected void setStat(Stat stat) {
      stat.name = path.name();
      statBell.ring(stat);
    }

    /**
     * Reconfigures its {@code path}. 
     * 
     * @param path new {@link Path} instance to be changed to
     */
    protected void setPath(String path) {
      resourcePath = path;
    }

    /*** Gets reconfigured {@code path}. */
    protected String getPath() {
      return resourcePath;
    }
  }
}
