package stork.core.commands;

import stork.util.*;
import stork.ad.*;
import static stork.util.StorkUtil.wrap;
import static stork.util.StorkUtil.joinWith;

import java.util.*;

/**
 * This is the base class for all command line commands, and includes code to
 * handle argument parsing and usage information generation.
 */
public abstract class Command {
  protected Command parent;

  protected String   prog = null;
  protected String[] args = null;
  protected String[] desc = null;
  protected String[] foot = null;

  private Map<String, Option> by_name = new HashMap<String, Option>();
  private Map<Character, Option> by_char = new HashMap<Character, Option>();
  private Map<String, CommandInfo> cmds =
    new HashMap<String, CommandInfo>();

  public Command() {
    this(null, null);
  } public Command(String prog) {
    this(null, prog);
  } public Command(Command parent) {
    this(parent, null);
  } public Command(Command parent, String prog) {
    this.parent = parent;
    this.prog = prog;
    // Every opts parse must have a help option.
    add("help", "display this usage information and exit").new
    Parser<Void>() {
      public Void handle() {
        usageAndExit(0, null);
        return null;
      }
    };
  }

  // A utility method for making a nicely wrapped option or command list
  // with descriptions, wrapped according to w (first column width) and
  // mw (total width).
  private static String wrapTable(String name, String desc, int w, int mw) {
    StringBuffer body = new StringBuffer();
    String fmt = "%-"+w+"s%s";
    String col2 = "";

    if (desc == null) {
      return name;
    } if (name.length()+2 > w) {
      body.append(name).append('\n');
      name = "";
    }

    // Maybe replace this with a matcher? Eh, who cares.
    for (String s : desc.split("\\s+")) {
      if (!col2.isEmpty() && col2.length()+s.length() >= mw-w) {
        body.append(String.format(fmt, name, col2)).append('\n');
        name = "";
        col2 = s;
      } else {
        col2 = (col2.isEmpty()) ? s : col2+' '+s;
      }
    }

    if (!col2.isEmpty())
      body.append(String.format(fmt, name, col2));

    return body.toString();
  }

  // A single option
  public class Option implements Comparable<Option> {
    char c = 0;
    String name;
    String desc = null;
    public Command.Parser<?> parser = null;

    private Option(String name) {
      this.name = name;
    }
  
    // Create a new parser in the context of this option automatically
    // associates it with the option.
    public abstract class Parser<T> extends Command.Parser<T> {
      public Parser() { this(null, false); }
      public Parser(String a) { this(a, false); }
      public Parser(String a, boolean r) { super(a, r); parser = this; }

      // Implement whichever one of these you want, I don't care.
      public T handle()           { return null; }
      public T handle(String arg) { return handle(); }
    }

    // This guy just returns whatever string gets passed to it.
    public class SimpleParser extends Parser<String> {
      public SimpleParser() { super(); }
      public SimpleParser(String a) { super(a); }
      public SimpleParser(String a, boolean r) { super(a, r); }

      public String handle(String arg) { return arg; }
    }

    // Get the usage parameters string sans description for this option.
    public String params() {
      String s = (c != 0) ? "  -"+c+", " : "      ";
      String a = parser == null || parser.arg == null ? "" :
                 parser.req     ? "="+parser.arg :
                                 "[="+parser.arg+"]";
      return s+"--"+name+a;
    }

    // Convert to a string with usage and description, with appropriate
    // wrapping according to w (first column width) and mw (total width).
    public String toString(int w, int mw) {
      return wrapTable(params(), desc, w, mw);
    }

    private String cname() {
      return c+name;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null) return false;
      if (o instanceof Option)
        return cname().equals(((Option)o).cname());
      return false;
    }

    public int compareTo(Option o) {
      return name.compareTo(o.name);
    }

    public int hashCode() {
      return cname().hashCode();
    }
  }

  // A parser that accepts an optional string argument, parses it, and
  // returns a value of a given type.
  public static abstract class Parser<T> {
    public final String  arg;  // The name of the argument.
    public final boolean req;  // Whether the argument is required.

    public Parser() { this(null, false); }
    public Parser(String a) { this(a, false); }
    public Parser(String a, boolean r) { arg = a; req = r; }

    // Implement whichever one of these you want, I don't care.
    public T handle()           { return null; }
    public T handle(String arg) { return handle(); }
  }

  // Wrapper for subcommand information.
  public class CommandInfo implements Comparable<CommandInfo> {
    String name, desc;
    Class<? extends Command> clazz;

    public CommandInfo(String s, String d, Class<? extends Command> c) {
      name = s;
      desc = d;
      clazz = c;
    }

    // Get a command object from this wrapper. If the wrapped command is
    // null, we should return a new instance of this Command's help command.
    public Command getCommand() {
      if (clazz == null) {
        return new HelpCommand();
      } try {
        return clazz.newInstance().parent(Command.this);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public String toString(int w, int mw) {
      return wrapTable("  "+name, desc, w+2, mw);
    }

    public int compareTo(CommandInfo o) {
      return name.compareTo(o.name);
    }

    public int hashCode() {
      return name.hashCode();
    }
  }

  // Execute the command with the given environment.
  public void execute(Ad opts) {
    usageAndExit(0, null);
  }

  // Override this to add argument checking. You can throw from this to
  // display usage and exit.
  protected void parseArgs(String[] args) { }

  // Used by parseArgs to assert arg length. Pass -1 to 
  protected final void assertArgsLength(String[] args, int n) {
    if (n == 0 && args.length != 0)
      throw OPT_EXCEPT("this commands takes no arguments");
    if (args.length < n)
      throw OPT_EXCEPT("not enough arguments -- takes exactly "+n);
    if (args.length > n)
      throw OPT_EXCEPT("too many arguments -- takes exactly "+n);
  } protected final void assertArgsLength(String[] args, int min, int max) {
    if (min > 0 && args.length < min)
      throw OPT_EXCEPT("not enough arguments -- need at least "+min);
    if (max > 0 && args.length > max)
      throw OPT_EXCEPT("too many arguments -- takes at most "+max);
  }

  // This thing should actually take an argument which is the name of a
  // subcommand, find its parser, and display its usage and exit.
  public class HelpCommand extends Command {
    private String cmd = null;
    public void parseArgs(String[] args) {
      assertArgsLength(args, 0, 1);
      if (args.length == 1)
        cmd = args[0];
    }
    public void execute(Ad opts) {
      if (cmd == null)
        Command.this.usageAndExit(0, null);
      else
        Command.this.getCommand(cmd).usageAndExit(0, null);
    }
    public void usageAndExit(int rc, String msg) {
      // For the cheeky people who do "stork help --help".
      Command.this.usageAndExit(rc, msg);
    }
  }

  // Add an option. To add a parser to the option, immediately after the
  // call to add, reference the parser member of the returned option and
  // create an anonymous opts parser class right there.
  public Option add(String n, String desc) {
    return add('\000', n, desc);
  } public Option add(char c, String n, String desc) {
    Option o = new Option(n);
    o.c = c; o.desc = desc;

    if (n == null || n.isEmpty())
      throw new RuntimeException("Option name cannot be null or empty!");
    if (c != 0 && by_char.containsKey(c))
      throw new RuntimeException("Option already exists with short name: "+c);
    if (n != null && by_name.containsKey(n))
      throw new RuntimeException("Option already exists with name: "+n);
    if (c != 0)
      by_char.put(c, o);
    by_name.put(n, o);
    return o;
  }

  // Add a subcommand class. Adding any commands to this parse will
  // automatically create a help command.
  public void add(String n, String d, Class<? extends Command> c) {
    if (cmds.size() == 0)
      createHelpCommand();
    cmds.put(n, new CommandInfo(n, d, c));
  } private void createHelpCommand() {
    String d = "get the usage info for a command";
    cmds.put("help", new CommandInfo("help", d, null));
  }

  // Check we're not forming a loop with the chain.
  private void checkChain(Command n) {
    if (n == this)
      throw new Error("Trying to create a loop in the GetOpt chain!");
    if (parent != null)
      parent.checkChain(n);
  }

  public Command parent(Command n) {
    if (n != parent) checkChain(n);
    parent = n;
    return this;
  }

  // Get information about this Command, checking down the chain if null.
  public String prog() {
    if (prog == null && parent != null)
      return parent.prog();
    if (parent != null)
      return joinWith(" ", parent.prog(), prog);
    return prog;
  }

  public String[] args() {
    if (args != null)
      return args;
    if (parent != null)
      return parent.args();
    return new String[0];
  }

  public String[] desc() {
    if (desc != null)
      return desc;
    if (parent != null)
      return parent.desc();
    return new String[0];
  }

  public String[] foot() {
    if (foot != null)
      return foot;
    if (parent != null)
      return parent.foot();
    return new String[0];
  }

  // Get the parser for a command, throwing an exception if the command is
  // not valid.
  public Command getCommand(String name) {
    try {
      return cmds.get(name).getCommand();
    } catch (Exception e) {
      throw OPT_EXCEPT("Invalid command: "+name);
    }
  }

  // This is a terrible hack to differentiate option parsing errors
  // from execution exceptions.
  private RuntimeException OPT_EXCEPT = null;
  private RuntimeException OPT_EXCEPT(String m) {
    return OPT_EXCEPT = new RuntimeException(m);
  }

  /** Parse command line arguments and execute the command. */
  public void parseAndExecute(String[] args) {
    try {
      parseAndExecuteInner(args);
    } catch (RuntimeException e) {
      if (e == OPT_EXCEPT) {
        usageAndExit(1, e.getMessage());
      } else {
        //e.printStackTrace();
        System.out.println("Error: "+e.getMessage());
      }
    }
  } private void parseAndExecuteInner(String[] args) {
    int i, j, n = args.length;
    String s, a = null;
    String[] sa;
    char[] c;
    Ad ad = new Ad();
    Option o = null;
    Parser<?> p = null;
    boolean end = false;  // Treat remaining args as positionals.
    List<String> list = new LinkedList<String>();  // Positional args.

    // If we have subcommands, resolve the subcommand parser.
    if (cmds.size() > 0) {
      String cmd = null;
      List<String> sl = new LinkedList<String>();

      for (String z : args) if (z.equals("--")) {
        end = true;
      } else if (cmd != null) {
        sl.add(z);
      } else if (end || !z.startsWith("-")) {
        cmd = z;
      } else {
        sl.add(z);
      }

      // If we found one, check if it's valid and parse with it instead.
      if (cmd != null) {
        getCommand(cmd).parseAndExecute(sl.toArray(new String[0]));
        return;
      }
    }

    // Oh boy here we go...
    for (i = 0; i < n; i++) if (args[i].equals("--")) {
      end = true;
    } else if (!end && args[i].startsWith("--")) {  // Long.
      sa = args[i].substring(2).split("=", 2);
      s = sa[0];
      a = (sa.length > 1) ? sa[1] : null;
      o = get(s);
      if (o == null)
        throw OPT_EXCEPT("Unrecognized option: '"+s+"'");
      if (ad.has(s))
        throw OPT_EXCEPT("Duplicate option: '"+s+"'");
      p = o.parser;
      if (p == null)
        ad.put(s, true);
      else if (a == null && p.req)
        throw OPT_EXCEPT("Argument required for '"+s+"'");
      else if (a == null)
        ad.put(s, p.handle());
      else
        ad.put(s, p.handle(a));
    } else if (!end && args[i].startsWith("-")) {  // Short.
      sa = args[i].substring(1).split("=", 2);
      c = sa[0].toCharArray();
      a = (sa.length > 1) ? sa[1] : null;
      for (j = 0; j < c.length; j++) {
        o = get(c[j]);
        if (o == null)
          throw OPT_EXCEPT("Unrecognized option: '"+c[j]+"'");
        s = o.name;
        if (ad.has(s))
          throw OPT_EXCEPT("Duplicate option: '"+c[j]+"' ("+s+")");
        p = o.parser;
        if (p == null)
          ad.put(s, true);
        else if (a == null && (p.req || j != c.length-1))
          throw OPT_EXCEPT("Argument required for '"+c[j]+"'");
        else if (a == null || j != c.length-1)
          ad.put(s, p.handle());
        else
          ad.put(s, p.handle(a));
      }
    } else {  // It's a positional argument so add it to the list.
      list.add(args[i]);
    }

    // Now pass rest of options to positional parameter parser.
    parseArgs(list.toArray(new String[list.size()]));

    execute(ad);
  }

  // Get a handler by name, checking down the chain. Returns null if
  // none found.
  private Option get(String name) {
    Option o = by_name.get(name);
    if (o == null && parent != null)
      return parent.get(name);
    return o;
  }

  // Likewise, get by short name.
  private Option get(char c) {
    Option o = by_char.get(c);
    if (o == null && parent != null)
      return parent.get(c);
    return o;
  }

  // Get a set of all options in the chain.
  private Set<Option> getOptions() {
    Set<Option> os = new TreeSet<Option>(by_name.values());
    if (parent != null)
      os.addAll(parent.getOptions());
    return os;
  }

  // Get a set of all the subcommands of these parser.
  private Set<CommandInfo> getCommands() {
    return new TreeSet<CommandInfo>(cmds.values());
  }

  // Print usage information then exit.
  public void usageAndExit(int rc, String msg) {
    usage(msg);
    System.exit(rc);
  }

  // Pretty print the usage information and optional message.
  public void usage(String msg) {
    int wrap = 78;  // TODO: Detect screen width/allow configuration.
    StringBuffer body = new StringBuffer();
    Set<Option> op_set = getOptions();
    Set<CommandInfo> cmd_set = getCommands();

    // Pretty any message if there is one.
    if (msg != null) {
      body.append(wrap(msg, wrap));
    }

    // Print the program usage header.
    String p = prog();
    if (p != null) {
      if (body.length() != 0) body.append("\n\n");
      String first =  "Usage: "+p+' ';
      String rest = "\n    or "+p+' ';
      body.append(first);
      body.append(joinWith(rest, (Object[]) args()));
    }

    // Print any subcommands.
    if (!cmd_set.isEmpty()) {
      int len = 3;
      // Get max length.
      for (CommandInfo c : cmd_set)
        if (c.name.length() > len) len = c.name.length();
      len += 4;

      if (body.length() != 0) body.append("\n\n");
      body.append("The following commands are available:");
      for (CommandInfo c : cmd_set)
        body.append('\n'+c.toString(len, wrap));
    }

    // Print the description.
    for (String s : desc())
      body.append("\n\n"+wrap(s, wrap));

    // Print the options.
    if (!op_set.isEmpty()) {
      if (body.length() != 0) body.append("\n\n");
      body.append("The following options are available:");
      for (Option o : op_set)
        body.append('\n'+o.toString(wrap/3, wrap));
    }

    // Finally print the footer.
    for (String s : foot())
      body.append("\n\n"+wrap(s, wrap));

    System.out.println(body);
  }
}
