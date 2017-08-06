package stork.ad;

import java.util.*;

/** Class for rendering ads in different ways. */
public class AdPrinter {
  // Options
  protected int indent = 2;  // Indentation size.

  // Default decorations.
  protected String
    LB  = "[\n", // Left bracket.
    RB  = "]",   // Right bracket.
    LLB = "{\n", // Left list bracket.
    RLB = "}",   // Right list bracket.
    EQ  = " = ", // Assignment token.
    SC  = ";\n", // Declaration separator.
    LSC = ",\n", // List item separator.
    ELT = "\n",  // Entry list terminator.
    IDL = "",    // Left ID decorator.
    IDR = "",    // Right ID decorator.
    STL = "\"",  // Left string decorator.
    STR = "\"";  // Right string decorator.

  // Some predefined ad formats.
  public static AdPrinter CLASSAD = new AdPrinter();
  public static AdPrinter CLASSAD_MIN = new AdPrinter() {
    AdPrinter set() {
      indent = 0;
      LB  = "[";  RB  = "]";  LLB = "{";
      RLB = "}";  EQ  = "=";  SC  = ";";
      LSC = ",";  IDL = "";   IDR = "";
      STL = "\""; STR = "\""; ELT = "";
      return this;
    }
  }.set();
  public static AdPrinter JSON = new AdPrinter() {
    AdPrinter set() {
      LB  = "{\n"; RB  = "}"; LLB = "[\n";
      RLB = "]"; EQ  = ": ";  SC  = ",\n";
      LSC = ",\n";   IDL = "\"";  IDR = "\"";
      STL = "\"";  STR = "\"";
      return this;
    }
  }.set();
  public static AdPrinter JSON_MIN = new AdPrinter() {
    AdPrinter set() {
      indent = 0;
      LB  = "{";  RB  = "}";  LLB = "[";
      RLB = "]";  EQ  = ":";  SC  = ",";
      LSC = ",";  IDL = "\""; IDR = "\"";
      STL = "\""; STR = "\""; ELT = "";
      return this;
    }
  }.set();

  AdPrinter set() { return this; }

  // Do the deed.
  public String toString(Ad ad) {
    return appendAd(new StringBuilder(), 0, ad).toString();
  } protected StringBuilder appendAd(StringBuilder sb, int level, Ad ad) {
    if (ad.isList()) {
      sb.append(LLB);
      appendEntries(sb, LSC, level+1, ad);
      indent(sb, level).append(RLB);
    } else {
      sb.append(LB);
      appendEntries(sb, SC, level+1, ad);
      indent(sb, level).append(RB);
    } return sb;
  }

  // Helpers
  // -------
  // Print the key/value entries into buffer. Assume brackets are printed
  // already.
  protected void appendEntries(StringBuilder sb, String s, int l, Ad ad) {
    boolean first = true;

    if (ad.isEmpty())
      return;

    if (ad.isMap()) for (Map.Entry<String, AdObject> e : ad.map().entrySet()) {
      if (!first)
        sb.append(s);
      first = false;
      indent(sb, l);
      if (e.getKey() instanceof String)
        appendKey(sb, e.getKey());
      appendValue(sb, l, e.getValue());
    } else if (ad.isList()) for (AdObject value : ad.list()) {
      if (!first)
        sb.append(s);
      first = false;
      indent(sb, l);
      appendValue(sb, l, value);
    } sb.append(ELT);
  }

  protected StringBuilder appendKey(StringBuilder sb, Object k) {
    sb.append(tokenKey(k));
    return sb.append(EQ);
  } protected StringBuilder appendValue(StringBuilder sb, int l, AdObject v) {
    if (v.isAd())
      return appendAd(sb, l, v.asAd());
    return sb.append(stringify(v));
  }

  // Represent key appropriately.
  protected String tokenKey(Object k) {
    if (AdParser.checkIdentifier(k.toString()))
      return IDR+k.toString()+IDL;
    return escapeString(k.toString());
  }

  // Indent based on the level.
  protected StringBuilder indent(StringBuilder sb, int level) {
    int space = level*indent;
    for (int i = 0; i < space; i++)
      sb.append(' ');
    return sb;
  }

  // Translate a Java string into an escaped and quoted string for
  // presentation.
  protected String escapeString(String s) {
    StringBuilder sb = new StringBuilder(s.length()+10).append(STR);
    char c;
    for (int i = 0; i < s.length(); i++) switch (c = s.charAt(i)) {
      case '"' : sb.append("\\\""); break;
      case '\\': sb.append("\\\\"); break;
      case '\n': sb.append("\\n");  break;
      case '\t': sb.append("\\t");  break;
      case '\r': sb.append("\\r");  break;
      case '\f': sb.append("\\f");  break;
      case '\b': sb.append("\\b");  break;
      default  : sb.append(c);
    }
    return sb.append(STL).toString();
  }

  // Convert an object into a parsable representation.
  protected String stringify(AdObject ao) {
    if (ao.isString())
      return escapeString(ao.asString());
    // Represent special numbers like Infinity as 0.
    if (ao.isSpecialNumber())
      return "0";
    return ao.asString();
  }
}
