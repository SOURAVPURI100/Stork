package stork.ad;

import java.io.*;
import java.util.*;
import java.math.*;
import java.nio.charset.*;

public class AdParser {
  int level = 0;
  char saved = 0;
  private Reader r;
  boolean body_only = false;
  private static RuntimeException eof =
    new RuntimeException("end of stream reached");
  public static Charset defaultCharset = Charset.forName("UTF-8");

  public static final String SEP = ",;& \n\r";
  public static final String EQ  = ":=";
  public static final String OB  = "{[(<";  // It's a fish!
  public static final String CB  = "}])>";
  public static final String WS  = " \t\n\r\b";

  // This includes decorator hints for printing. Or will, maybe. For now
  // it just picks a printer based on the opening bracket. The hint should
  // be set after at least one object is inserted so we know whether this
  // ad is a map or list.
  public static class ParsedAd extends Ad {
    public AdPrinter printer = AdPrinter.CLASSAD;
    public AdPrinter setHint(char c) {
      if (isList()) switch (c) {
        case '[': return printer = AdPrinter.JSON;
        case '{': return printer = AdPrinter.CLASSAD;
      } else switch (c) {
        case '{': return printer = AdPrinter.JSON;
        case '[': return printer = AdPrinter.CLASSAD;
      } return printer;
    }
    public String toString() {
      return printer.toString(this);
    }
  }

  AdParser(CharSequence s, boolean body_only) {
    this(new StringReader(s.toString()), body_only);
  } AdParser(InputStream is, boolean body_only) {
    this(new InputStreamReader(is, defaultCharset), body_only);
  } AdParser(Reader r, boolean body_only) {
    this.r = (r instanceof BufferedReader) ? r : new BufferedReader(r);
    if (this.body_only = body_only) saved = '[';
  }

  // Utility methods
  // ---------------
  // Get the next character, throwing an unchecked exception on error.
  private char next() {
    try {
      int i = (saved != 0) ? saved : r.read();
      saved = 0;
      if (i <= -1) {
        if (body_only)
          return ']';  // Just a little hacky.
        throw eof;
      } return (char) i;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // Peek at the next character, then save it.
  private char peek() {
    char c = next();
    return saved = c;
  }

  // Check if a string or range contains a character.
  private static boolean check(char c, String s) {
    return s.indexOf(c) >= 0;
  } private static boolean check(char c, char from, char to) {
    if (from > to) return check(c, to, from);
    return c >= from && c <= to;
  }

  // Discard characters from the given set. Return the first thing that
  // isn't part of the ignore set.
  private char discard() {
    // Discard whitespace by default.
    return discard(WS);
  } private char discard(String s) {
    char c;
    for (c = next(); check(c, s); c = next());
    return c;
  }

  // Discard ignored characters as well as comments.
  private char discardIgnored() {
    return discardIgnored(WS);
  } private char discardIgnored(String s) {
    char c;
    do switch (c = discard(s)) {
      case '/': if (peek() != '/') return c;
      case '#': for (c = peek(); !check(c, "\r\n"); c = next());
    } while (check(c, s));
    return c;
  }

  // Ignore all whitespace and check for a character in the given set.
  // If something else is found, throws a parse error.
  private char expect(String s) {
    return expect(s, WS);
  } private char expect(String s, String i) {
    char c = discardIgnored(i);
    if (!check(c, s))
      throw new RuntimeException("unexpected character: "+c);
    return c;
  }

  // Lowercase a character assuming it's an A-Z letter.
  private static char low(char c) {
    return (char)((short)c | (short)' ');
  }

  // Parsing methods
  // ---------------
  public Ad parse() {
    try {
      return parseInto(new ParsedAd());
    } catch (RuntimeException e) {
      if (e == eof)
        return null;
      throw e;
    }
  }

  public Ad parseInto(Ad ad) {
    char open = expect(OB);

    for (int i = 0; ; i++) {
      char c = saved = discardIgnored(SEP+WS);

      // Check for end of ad.
      if (check(c, CB)) {
        next();
        return ad;
      }

      // Discard any extraneous separators or newlines.
      saved = discardIgnored(SEP+WS);

      // Read the first token.
      Object o = readValue();

      // Check if it's anonymous or not.
      // FIXME: These switch cases should not be hardcoded...
      switch (c = expect(EQ+SEP+CB, " \t\b")) {
        case ':': // Check for assignment.
        case '=':
          // Determine the key.
          String k = o.toString();
          o = findValue();
          // Insert into ad as key-value pair.
          if (o instanceof Atom)
            ad.putObject(k, ((Atom)o).eval());
          else
            ad.putObject(k, o);
          if (open > 0 && ad instanceof ParsedAd)
            ((ParsedAd)ad).setHint(open);
          open = 0;
          break;
        case '}': // Check for end and push char back if found.
        case ']':
        case ')':
        case '>': saved = c;
        case ',': // Check for separator.
        case ';':
        case '&':
        case ' ':
        case '\r':
        case '\n':
          // Insert into ad as list item.
          if (o instanceof Atom)
            ad.putObject(((Atom)o).eval());
          else
            ad.putObject(o);
          if (open > 0 && ad instanceof ParsedAd)
            ((ParsedAd)ad).setHint(open);
          open = 0;
      }
    }
  }

  // Find and unescape a string.
  private String readString() {
    StringBuilder sb = new StringBuilder();
    char c = next();
    if (c != '"')
      throw new RuntimeException("expecting start of string");
    while (true) switch (c = next()) {
      case '"' : return sb.toString();
      case '\\': sb.append(readEscaped()); continue;
      default:
        if (Character.isISOControl(c))
          throw new RuntimeException("illegal character in string");
        sb.append(c);
    }
  }

  // Find an escaped character, assuming the \ has already been read.
  private char readEscaped() {
    switch (next()) {
      case '"' : return '"';
      case '\\': return '\\';
      case '/' : return '/';
      case 'b' : return '\b';
      case 'f' : return '\f';
      case 'n' : return '\n';
      case 'r' : return '\r';
      case 't' : return '\t';
      case 'u' : {
        int r = 0;
        for (int i = 0; i < 4; i++) {
          char c = next();
          if (check(c, '0', '9'))
            r = (r << 4) | (c-'0');
          else if (check(c = low(c), 'a', 'f'))
            r = (r << 4) | (c-'a'+10);
          else throw new RuntimeException("illegal escape sequence");
        } return (char) r;
      }
    } throw new RuntimeException("illegal escape sequence");
  }

  // An atom represents something that can be either an identifier or a
  // keyword. The string representation of this should be lowercased for
  // use as a key.
  private static class Atom {
    String s;
    Atom(String s) { this.s = s; }
    public String toString() { return s.toLowerCase(); }
    public Object eval() {
      String sl = s.toLowerCase();
      if (sl.equals("false"))
        return Boolean.FALSE;
      if (sl.equals("true"))
        return Boolean.TRUE;
      if (sl.equals("null"))
        return null;
      return s;
    }
  }

  // Try to read an atom.
  private Atom readAtom() {
    StringBuilder sb = new StringBuilder();
    for (char c = peek(); validAtomPart(c); c = peek())
      sb.append(next());
    return new Atom(sb.toString());
  }

  // Check if a character can start or be in an atom.
  private static boolean validAtomStart(char c) {
    return check(low(c), 'a', 'z') || c == '_';
  } private static boolean validAtomPart(char c) {
    return check(low(c), 'a', 'z') || c == '_' || check(c, '0', '9');
  }

  // Check if a string is a valid identifier.
  public static boolean checkIdentifier(String s) {
    for (char i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (i == 0 && !validAtomStart(c) || !validAtomPart(c))
        return false;
    } return true;
  }

  // Find a value, and return it as a Java object.
  private Object findValue() {
    saved = discardIgnored();
    return readValue();
  } private Object readValue() {
    char c = peek();
    switch (c) {
      case '"': return readString();
      case '-': return readNumber();
    } if (check(c, '0', '9')) {
      return readNumber();
    } if (check(c, "{[(<")) {
      return parseInto(new Ad());
    } if (validAtomStart(c)) {
      return readAtom();
    } throw new RuntimeException("cannot parse value starting with: "+c);
  }

  private Number readNumber() {
    char c;
    boolean d = false;
    StringBuilder sb = new StringBuilder();
    while (true) switch (c = peek()) {
      case '.':
      case 'e':
      case 'E':
      case '+':
        d = true;
      case '-':
        sb.append(next());
        continue;
      default:
        if (check(c, '0', '9'))
          sb.append(next());
        else if (check(c, ",:; \n\r\t>}])")) {
          return d ? new BigDecimal(sb.toString()):
                     new BigInteger(sb.toString());
        }
        else
          throw new RuntimeException("unexpected character: "+c);
    }
  }
}
