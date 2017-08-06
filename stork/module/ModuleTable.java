package stork.module;

import java.util.*;
import java.io.*;

import stork.ad.*;
import stork.util.*;
import static stork.util.StorkUtil.join;

/** A class for looking up transfer modules by protocol and handle. */
// TODO: Move logging outside of this class.
public class ModuleTable {
  private Map<String, Module> byProto, byHandle;

  public ModuleTable() {
    byProto  = new HashMap<String, Module>();
    byHandle = new HashMap<String, Module>();
  }

  /** Automatically populate the module table. */
  public void populate() {
    // Load built-in modules.
    // TODO: Automatic discovery for built-in modules.
    register(new stork.module.ftp.FTPModule());
    register(new stork.module.irods.IRODSModule());
    register(new stork.module.http.HTTPModule());
    register(new stork.module.dropbox.DbxModule());
    register(new stork.module.sftp.SFTPModule());

    // FIXME: External modules disabled for now...
    //if (config.libexec != null)
      //modules.registerDirectory(new File(config.libexec));

    // Check if anything got added.
    if (byProto.isEmpty())
      Log.warning("No transfer modules registered.");
  }

  /**
   * Add a directory of executables to the transfer module table.
   * TODO: Do this in parallel and detect misbehaving externals.
   */
  public void registerDirectory(File dir) {
    if (!dir.isDirectory()) {
      Log.warning('"', dir, "\" is not a directory!");
    } else for (File f : dir.listFiles()) {
      // Skip over things that obviously aren't transfer modules.
      if (f.isFile() && !f.isHidden() && f.canExecute())
        register(f);
    }
  }

  /** Add an external transfer module to the table. */
  public void register(File f) {
    //register(new ExternalModule(f));
  }

  /** Add a transfer module to the table. */
  public void register(Module m) {
    // Check if handle is in use.
    if (!byHandle.containsKey(m.handle())) {
      byHandle.put(m.handle(), m);
      Log.info("Registered module \"", m, "\" [handle: ", m.handle(), "]");
    } else {
      Log.warning("Module handle \"", m.handle(), "\"in use, ignoring");
      return;
    }

    Set<String> good = new TreeSet<String>();
    Set<String> bad  = new TreeSet<String>();

    // Add the protocols for this module.
    for (String p : m.protocols()) {
      p = p.toLowerCase();
      if (!byProto.containsKey(p)) {
        good.add(p);
        byProto.put(p, m);
      } else {
        bad.add(p);
      }
    }

    if (!good.isEmpty())
      Log.info("  Registering protocol(s): ", good);
    if (!bad.isEmpty())
      Log.info("  Protocols already registered: ", bad);
  }

  /** Get a map of transfer modules by handle. */
  public Map<String,Module> modulesByHandle() {
    return Collections.unmodifiableMap(byHandle);
  }

  /** Get a transfer module by its handle. */
  public Module byHandle(String h) {
    Module m = byHandle.get(h);
    if (m != null)
      return m;
    throw new RuntimeException("No module '"+h+"' registered.");
  }

  /** Get a transfer module by protocol. */
  public Module byProtocol(String p) {
    Module m = byProto.get(p);
    if (m != null)
      return m;
    throw new RuntimeException("No module for protocol '"+p+"' registered.");
  }

  /** Get a set of all the modules. */
  public Collection<Module> modules() {
    return byHandle.values();
  }

  /** Get a set of all the handles. */
  public Set<String> handles() {
    return byHandle.keySet();
  }

  /** Get a set of all the supported protocols. */
  public Set<String> protocols() {
    return byProto.keySet();
  }
}
