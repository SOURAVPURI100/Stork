package stork.module.ftp;

/**
 * FTP listing commands in order of priority.
 */
public enum FTPListCommand {
  MLSC(true, true),
  STAT(true, true),
  MLSD(false, true),
  MLST(false, false),
  LIST(false, true),
  NLST(false, true);

  private boolean cc, lists;

  private FTPListCommand(boolean cc, boolean lists) {
    this.cc = cc;
    this.lists = lists;
  }

  /**
   * Get the listing command that should be used after this one. Or {@code
   * null} if this is the least preferable one.
   */
  public FTPListCommand next() {
    final int n = ordinal()+1;
    return (n < values().length) ? values()[n] : null;
  }

  /** Check if this listing command requires a data channel. */
  public boolean requiresDataChannel() {
    return !cc;
  }

  /** Check if the command can list subresource names. */
  public boolean canList() {
    return lists;
  }
}
