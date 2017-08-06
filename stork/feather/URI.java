package stork.feather;

import java.util.*;

import stork.feather.util.*;

/**
 * A representation of a Uniform Resource Identifier (URI). The getter methods
 * of this class which return single components will return unescaped (decoded)
 * representations, and likewise the single-component setter methods expect
 * unescaped inputs; the getter methods which return concatenated component
 * groups will return representations with escaped (encoded) subcomponents and
 * unescaped joiners, and likewise the component group setter methods expect
 * escaped inputs with unescaped joiners.
 * <p/>
 * The syntax of URIs in Feather conform to the URI generic syntax defined in
 * RFC 3986. However, some non-standard component groups are also defined for
 * URLs, as shown in this diagram:
 *
 * <blockquote><pre>
 *                       authority
 *                  _________|__________
 *                 /                    \
 *                 userinfo      hostport
 *             _______|_______   ___|___
 *            /               \ /       \
 *   scheme://username:password@host:port/path?query#fragment
 *   \__________________________________/\__________________/
 *                     |                          |
 *                  endpoint                   resource
 * </pre></blockquote>
 *
 * The API is inspired heavily by URI.js, which can be found here:
 *
 * <blockquote>{@code https://github.com/medialize/URI.js}</blockquote>
 */
public class URI {
  private final String scheme;
  private final String userinfo;
  private final String host;
  private final int    port;
  private final Path   path;
  private final String query;
  private final String fragment;

  //private static Intern<URI> intern = new Intern<URI>();

  /** Used to quickly determine if a character is reserved. */
  private static final BitSet RESERVED = new BitSet(128);
  static {
    for (char c : " !#$%&'()*+,/:;=?@[]{}".toCharArray())
      RESERVED.set(c);
  }

  /** Java URI to resolve against to handle missing schemes. */
  private static final java.net.URI noSchemeBaseURI;
  static {
    try {
      noSchemeBaseURI = new java.net.URI(null, null, null);
    } catch (Exception e) {
      // Let's hope this never happens.
      throw new Error("java.net.URI is broken");
    }
  }

  /** The canonical empty URI. */
  public static final URI EMPTY = new URI();

  private URI() {
    scheme   = null;
    userinfo = null;
    host     = null;
    port     = -1;
    path     = null;
    query    = null;
    fragment = null;
  }

  private URI(String uri) {
    // For now, delegate to java.net.URI...
    java.net.URI u = noSchemeBaseURI.resolve(uri);

    scheme   = u.getScheme();
    userinfo = u.getUserInfo();
    host     = u.getHost();
    port     = u.getPort();
    path     = Path.create(u.getPath());
    query    = u.getQuery();
    fragment = u.getFragment();
  }

  private URI(URIBuilder builder) {
    this.scheme   = builder.scheme;
    this.userinfo = builder.userinfo;
    this.host     = builder.host;
    this.port     = builder.port;
    this.path     = builder.path;
    this.query    = builder.query;
    this.fragment = builder.fragment;
  }

  class URIBuilder {
    String scheme   = URI.this.scheme;
    String userinfo = URI.this.userinfo;
    String host     = URI.this.host;
    int    port     = URI.this.port;
    Path   path     = URI.this.path;
    String query    = URI.this.query;
    String fragment = URI.this.fragment;

    URI toURI() { return new URI(this); }
  }

  /**
   * Return a canonical representation of the URI.
   *
   * @param uri the URI which should be interned.
   * @return A canonical representation of {@code uri}.
   */
  //public static URI intern(URI uri) {
    //return intern.intern(uri);
  //}

  /**
   * Create a URI from the given string representation.
   *
   * @param uri a string representation of the URI to parse.
   * @return A parsed URI.
   */
  public static URI create(String uri) {
    //return intern.intern(new URI(uri));
    return new URI(uri);
  }

  /**
   * Convert this URI into a Java URI.
   *
   * @return A {@link java.net.URI} version of this URI.
   */
  public java.net.URI toJavaURI() {
    return java.net.URI.create(toString());
  }

  /**
   * Get the scheme (or protocol) component of this URI.
   *
   * @return The scheme component of this URI, or {@code null} if undefined.
   */
  public String scheme() { return scheme; }

  /**
   * Return a URI based on this one with the given scheme (or protocol)
   * component.
   *
   * @param scheme the scheme for the new URI.
   * @return A URI based on this one with the given scheme component.
   */
  public URI scheme(String scheme) {
    return new URIBuilder() {{ this.scheme = scheme; }}.toURI();
  }

  /**
   * Get the protocol (or scheme) component of this URI. This is an alias for
   * {@link #scheme()}.
   *
   * @return The protocol component of this URI, or {@code null} if
   * undefined.
   */
  public final String protocol() {
    return scheme();
  }

  /**
   * Return a URI based on this one with the given protocol (or scheme)
   * component. This is an alias for {@link #scheme(String)}.
   *
   * @param protocol the protocol for the new URI.
   * @return A URI based on this one with the given scheme component.
   */
  public final URI protocol(String protocol) {
    return scheme(protocol);
  }

  /**
   * Get the username associated with this URI, if this is a URL.
   *
   * @return The username according to the user-info segment of this URI, or
   * {@code null} if none is specified.
   */
  public String username() {
    return userPass()[0];
  }

  /**
   * Return a URI based on this one with the given username component.
   *
   * @param username the new username.
   * @return A URI based on this one with the given username component.
   */
  public URI username(String username) {
    return userPass(username, password());
  }

  /**
   * Get the username associated with this URI, if this is a URL. This is an
   * alias for {@link #username()}.
   *
   * @return The username according to the user-info segment of this URI, or
   * {@code null} if none is specified.
   */
  public final String user() {
    return username();
  }

  /**
   * Return a URI based on this one with the given username component. This
   * is an alias for {@link #username(String)}.
   *
   * @param username the new username.
   * @return A URI based on this one with the given username component.
   */
  public final URI user(String username) {
    return username(username);
  }

  /**
   * Get the password associated with this URI, if this is a URL.
   *
   * @return The password according to the user-info segment of this URI, or
   * {@code null} if none is specified.
   */
  public String password() {
    return userPass()[1];
  }

  /**
   * Return a URI based on this one with the given password component.
   *
   * @param password the new password.
   * @return A URI based on this one with the given password component.
   */
  public URI password(String password) {
    return userPass(username(), password);
  }

  /**
   * Get the password associated with this URI, if this is a URL. This is an
   * alias for {@link #password()}.
   *
   * @return The password according to the user-info segment of this URI, or
   * {@code null} if none is specified.
   */
  public final String pass() {
    return password();
  }

  /**
   * Return a URI based on this one with the given password component. This is
   * an alias for {@link #password(String)}.
   *
   * @param password the new password.
   * @return A URI based on this one with the given password component.
   */
  public final URI pass(String password) {
    return password(password);
  }

  /**
   * Return the user-info segment split and decoded into a separated username
   * and password. If there is no user-info segment, the returned array
   * contains two {@code null}s.
   *
   * @return A {@code String[2]} containing the username and password (in that
   * order). If either components was not specified in the URI, the
   * corresponding member of the returned array will be {@code null}.
   */
  public String[] userPass() {
    String cs = userInfo();
    if (cs == null)
      return new String[2];
    int i = cs.indexOf(':');
    return new String[] {
      decode((i < 0) ? cs   : (i > 0) ? cs.substring(0,i) : null),
      decode((i < 0) ? null : cs.substring(i+1))
    };
  }

  /**
   * Return a URI based on this one with the given username and password
   * component.
   *
   * @param userpass the new username and password.
   * @return A URI based on this one with the given user-info component.
   */
  public URI userPass(String... userpass) {
    String u = (userpass.length > 0) ? userpass[0] : null;
    String p = (userpass.length > 1) ? userpass[1] : null;
    return userInfo(encode(u)+":"+encode(p));
  }

  /**
   * Get the user-info segment in the form {@code "[user][:pass]"}, if this is
   * a URL. If the password is not included in the URI, this is equivalent to
   * {@link #username()}. If the username is not included in the URI, this
   * returns the result of {@link #password()} preceded with a colon.
   *
   * @return The user-info segment of the URI.
   */
  public String userInfo() { return userinfo; }

  /**
   * Return a URI based on this one with the given user-info component.
   *
   * @param userinfo the new user-info.
   * @return A URI based on this one with the given user-info component.
   */
  public URI userInfo(String userinfo) {
    return new URIBuilder() {{ this.userinfo = userinfo; }}.toURI();
  }

  /**
   * Get the host from the authority segment of the URI, if this is a URL.
   *
   * @return The host according to the authority segment of the URI, or {@code
   * null} if none is specified.
   */
  public String host() { return host; }

  /**
   * Return a URI based on this one with the given host component.
   *
   * @param host the new host, unescaped and unbracketed.
   * @return A URI based on this one with the given host component.
   */
  public URI host(String host) {
    return new URIBuilder() {{ this.host = host; }}.toURI();
  }

  /**
   * Get the port from the authority segment of the URI, if this is a URL.
   *
   * @return The port according to the authority segment of the URI, or {@code
   * -1} if none is specified.
   */
  public int port() { return port; }

  /**
   * Return a URI based on this one with the given port component.
   *
   * @param port the new port.
   * @return A URI based on this one with the given port component.
   */
  public URI port(int port) {
    return new URIBuilder() {{ this.port = port; }}.toURI();
  }

  /**
   * Get the host and port in the form {@code host[:port]}. The returned string
   * will also be properly bracketed according to RFC 2732 for hosts containing
   * colons (e.g., IPv6 addresses).
   *
   * @return The concatenated host and port according to the authority segment
   * of the URI, or just the host if no port is specified. If neither are
   * specified, {@code null} is returned.
   */
  public String hostPort() {
    String h = host();
    int p = port();
    if (h != null && h.indexOf(':') >= 0) h = "["+h+"]";  // For IPv6 hosts.
    if (p <= 0) return h;  // May return null.
    if (h == null) h = "";
    return h+":"+p;
  }

  /**
   * Return a URI based on this one with the given host and port components.
   *
   * @param hostport the new host, escaped and bracketed, concatenated with the
   * port with a colon separator.
   * @return A URI based on this one with the given host and port components.
   */
  public URI hostPort(String hostport) {
    if (hostport == null)
      return hostPort(null, -1);
    String[] hp = hostport.split(":");
    return hostPort(hp[0], hp[1]);
  }

  /**
   * Return a URI based on this one with the given host and port components.
   *
   * @param host the new host, unescaped and unbracketed.
   * @param port the new port as a string.
   * @return A URI based on this one with the given host and port components.
   */
  public URI hostPort(String host, String port) {
    try {
      return hostPort(host, Integer.parseInt(port));
    } catch (Exception e) {
      return hostPort(host, -1);
    }
  }

  /**
   * Return a URI based on this one with the given host and port components.
   *
   * @param host the new host, unescaped and unbracketed.
   * @param port the new port.
   * @return A URI based on this one with the given host and port components.
   */
  public URI hostPort(String host, int port) {
    return new URIBuilder() {{
      this.host = host;
      this.port = port;
    }}.toURI();
  }

  /**
   * Get the authority component of the URI in the form of:
   *
   * <blockquote>
   * {@code [[user][:pass]{@literal}][host[:port]]}.
   * </blockquote>
   *
   * This method will return {@code null} if the URI does not have an authority
   * component.
   *
   * @return The authority component of the URI, or {@code null} if none is
   * specified.
   */
  public String authority() {
    String ui = userInfo();
    String hp = hostPort();
    if (ui == null) return hp;  // May return null.
    if (hp == null) hp = "";
    return ui+"@"+hp;
  }

  /**
   * Return a URI based on this one with the given authority segment.
   *
   * @param authority the new authority component.
   * @return A URI based on this one with the given authority component.
   */
  public URI authority(String authority) {
    if (authority == null)
      return userInfo(null).hostPort(null);
    URI u = create(authority);
    return userInfo(u.userInfo()).hostPort(u.hostPort());
  }

  /**
   * Get the path segment of the URI, if it is a URL.
   *
   * @return The path segment of this URI. This will never be {@code null}.
   */
  public Path path() {
    return path != null ? path : Path.ROOT;
  }

  /**
   * Return a URI based on this one with the given path segment.
   *
   * @param path the new path, with escaped segments.
   * @return A URI based on this one with the given path component.
   */
  public URI path(String path) {
    return path(Path.create(path));
  }

  /**
   * Return a URI based on this one with the given path segment.
   *
   * @param path the new path.
   * @return A URI based on this one with the given path component.
   */
  public URI path(Path path) {
    final Path np = path;
    return new URIBuilder() {{ this.path = np; }}.toURI();
  }

  /**
   * Return a URI based on this one with the given path segment appended.
   *
   * @param path the path to append, as an escaped string.
   * @return A URI based on this one with the given path appended.
   */
  public URI append(String path) {
    Path p = path();
    return (p == null) ? path(Path.create(path)) : path(p.append(path));
  }

  /**
   * Return a URI based on this one with the given path segment appended.
   *
   * @param path the path to append.
   * @return A URI based on this one with the given path appended.
   */
  public URI append(Path path) {
    Path p = path();
    return (p == null) ? path(path) : path(p.append(path));
  }

  /**
   * Return a URI based on this one with the given path segment appended.
   *
   * @param name the literal name of the segment to append.
   * @return A URI based on this one with the given path segment appended.
   */
  public URI appendLiteral(String name) {
    Path p = path();
    return (p == null) ? path(Path.create(encode(name))) :
                         path(p.appendLiteral(name));
  }

  /**
   * Return a URI based on this one with the last path segment removed.
   *
   * @return A URI based on this one with the last path segment removed.
   */
  public URI up() {
    Path p = path();
    return (p == null) ? this : path(p.up());
  }

  /**
   * Get the query component of the URI, if it is a URL.
   *
   * @return The query component of this URI, or {@code null} if none is
   * specified.
   */
  public String query() { return query; }

  /**
   * Return a URI based on this one with the given query component.
   *
   * @param query the new query string.
   * @return A URI based on this one with the given query segment.
   */
  public URI query(String query) {
    return new URIBuilder() {{ this.query = query; }}.toURI();
  }

  /**
   * Get the fragment component of the URI, if it is a URL.
   *
   * @return The fragment component of this URI, or {@code null} if none is
   * specified.
   */
  public String fragment() { return fragment; }

  /**
   * Return a URI based on this one with the given fragment component.
   *
   * @param fragment the new fragment string.
   * @return A URI based on this one with the given fragment component.
   */
  public URI fragment(String fragment) {
    return new URIBuilder() {{ this.fragment = fragment; }}.toURI();
  }

  /**
   * Get the hash (fragment) component of the URI, if it is a URL. This is an
   * alias for {@link #fragment()}.
   *
   * @return The hash component of this URI, or {@code null} if none is
   * specified.
   */
  public final String hash() {
    return fragment();
  }

  /**
   * Return a URI based on this one with the given hash (fragment) component.
   * This is an alias for {@link #fragment(String)}.
   *
   * @param hash the new hash string.
   * @return A URI based on this one with the given hash component.
   */
  public final URI hash(String hash) {
    return fragment(hash);
  }

  /**
   * Get the resource component of the URI, if it is a URL. The returned string
   * is in the form of:
   *
   * <blockquote>
   * {@code [path][?query][#fragment]}
   * </blockquote>
   *
   * This method will return {@code null} if the URI does not have a resource
   * component.
   */
  public String resource() {
    StringBuilder sb = new StringBuilder();
    Path p = path();
    String q = encode(query());
    String f = encode(fragment());

    if (p == null && q == null && f == null) return null;

    if (p != null) sb.append(p);
    if (q != null) sb.append("?").append(q);
    if (f != null) sb.append("#").append(f);

    return sb.toString();
  }

  /**
   * Return a URI based on this one with the given resource component.
   *
   * @param resource the new resource string.
   * @return A URI based on this one with the given resource component.
   */
  public URI resource(String resource) {
    if (resource == null)
      return path((Path)null).query(null).fragment(null);
    URI u = create(resource);
    return path(u.path()).query(u.query()).fragment(u.fragment());
  }

  /**
   * Return a URI containing only the resource components of this URI.
   *
   * @return A URI based on this one with only the resource components, or
   * {@code null} if there are no resource components.
   */
  public URI resourceURI() {
    return endpoint(null).nonEmpty();
  }

  /**
   * Get the endpoint component of the URI, if it is a URL. The returned string
   * is in the form of:
   *
   * <blockquote>
   * {@code [scheme:][//authority]}
   * </blockquote>
   *
   * This method will return {@code null} if the URI does not have an endpoint
   * component.
   */
  public String endpoint() {
    StringBuilder sb = new StringBuilder();
    String s = encode(scheme());
    String a = authority();

    if (s == null && a == null) return null;

    if (s != null) sb.append(s).append(":");
    if (a != null) sb.append("//").append(a);

    return sb.toString();
  }

  /**
   * Return a URI based on this one with the given endpoint component.
   *
   * @param endpoint the new endpoint string.
   * @return A URI based on this one with the given endpoint component.
   */
  public URI endpoint(String endpoint) {
    if (endpoint == null)
      return scheme(null).authority(null);
    URI u = create(endpoint);
    return scheme(u.scheme()).authority(u.authority());
  }

  /**
   * Return a URI containing only the endpoint components of this URI.
   *
   * @return A URI based on this one with only the endpoint components, or
   * {@code null} if there are no endpoint components.
   */
  public final URI endpointURI() {
    return resource(null).nonEmpty();
  }

  /**
   * Check if this is an empty URI. That is, if all of its components are
   * {@code null}, or a URI whose string representation is an empty string.
   *
   * @return {@code true} if the URI is empty; {@code false} otherwise.
   */
  public boolean isEmpty() {
    return toString().isEmpty();
  }

  /**
   * Return this URI if it is non-empty, or {@code null} if it is empty.
   *
   * @return This URI if it is non-empty, or {@code null} if it is empty.
   */
  private final URI nonEmpty() {
    return isEmpty() ? null : this;
  }

  /**
   * Return whether or not the URI is absolute. An absolute URI has no scheme
   * component.
   *
   * @return {@code true} if this URI is absolute; {@code false} otherwise.
   */
  public final boolean isAbsolute() {
    return scheme() != null;
  }

  /**
   * Return whether or not the URI is relative. A relative URI has a scheme
   * component.
   *
   * @return {@code true} if this URI is relative; {@code false} otherwise.
   */
  public final boolean isRelative() {
    return scheme() == null;
  }

  /**
   * Return a URI based on this one relativized to {@code base}. If there is no
   * overlap between this URI and {@code base}, this URI is returned. TODO
   *
   * @param base the base URI to relativize this URI to.
   * @return A URI based on this one, relative to {@code base}.
   */
  public URI relativeTo(URI base) {
    return this;
  }

  /**
   * Resolve the given URI against this URI.
   *
   * @param uri the URI to resolve against this URI.
   * @return A URI resolved against this one.
   */
  public URI resolve(URI uri) {
    if (uri.isAbsolute())
      return uri;
    URIBuilder t = new URIBuilder();
    throw new Error("Not implemented");
  }

  /**
   * Get the hierarchical component of the URI. The returned string is in the
   * form of:
   *
   * <blockquote>
   * {@code [[authority]path]}
   * </blockquote>
   *
   * This method will return {@code null} if the URI does not have a
   * hierarchical component.
   */
  public String hierarchicalPart() {
    String a = authority();
    String p = path().toString();
    if (a == null) return p;
    if (p == null) p = "";
    return a+p;
  }

  /**
   * Return a properly escaped string representation of this URI. This string
   * is generated based on the components of the URI. It should be possible to
   * create an identical URI by passing the returned string to {@link
   * #create(String)}.
   */
  public String toString() {
    StringBuilder sb = new StringBuilder();
    String s = scheme();
    String a = authority();
    String r = resource();

    if (s != null) sb.append(s).append(":");
    if (a != null) sb.append("//").append(a);
    if (r != null) sb.append(r);

    return sb.toString();
  }

  /**
   * Return an encoded representation of {@code string}.
   *
   * @param string an input {@code String} to encode.
   * @return An encoded representation of {@code string}.
   */
  public static String encode(String string) {
    if (string == null)
      return null;
    StringBuilder sb = new StringBuilder();
    int length = string.length();
    for (int offset = 0; offset < length;) {
      int cp = string.codePointAt(offset);
      if (shouldEncode(cp))
        sb.append(encode(cp));
      else
        sb.appendCodePoint(cp);
      offset += Character.charCount(cp);
    }
    return sb.toString();
  } private static boolean shouldEncode(int c) {
    return c < ' ' || c > '~' || RESERVED.get(c);
  }

  /**
   * Percent-encode a code point, regardless of whether or not it is a reserved
   * character.
   *
   * @param c the code point to encode.
   * @return The percent-encoding of {@code c}.
   */
  public static String encode(int c) {
    StringBuilder sb = new StringBuilder();
    do {
      int b = c & 0xFF;
      c >>>= 8;
      sb.append("%").append(Integer.toString(b, 16).toUpperCase());
    } while (c != 0);
    return sb.toString();
  }

  /**
   * Return an unescaped version of the given escaped string.
   *
   * @param string an escaped input string to unescape, which may be {@code
   * null}.
   * @return An unescaped representation of {@code string}, or {@code null} if
   * {@code string} is {@code null}.
   */
  public static String decode(String string) {
    if (string == null)
      return null;
    return string;
  }

  /**
   * Test this URI for equivalence to another object. A URI is equivalent to
   * another URI if they are component-wise equal.
   *
   * @param object the object to test equivalence with.
   * @return {@code true} if this URI is equivalent to {@code object}; {@code
   * false} otherwise.
   */
  public boolean equals(Object object) {
    if (!(object instanceof URI))
      return false;
    URI u = (URI) object;
    return toString().equals(u.toString());
  }

  /**
   * Generate a hash code for this URI. A URI's hash code should be the same as
   * the hash code of its string representation.
   *
   * @return A hash code for this URI.
   */
  public int hashCode() {
    return toString().hashCode();
  }
}
