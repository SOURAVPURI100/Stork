package stork.module.ftp;

import stork.feather.*;
import stork.module.*;

public class FTPModule extends Module<FTPResource> {
  {
    name("Stork FTP Module");
    protocols("ftp", "gsiftp", "gridftp");
    description(
      "A module for interacting with FTP systems and derivatives thereof.",
      "Supports RFC 2228 security extensions with GSSAPI, as well as a few",
      "GridFTP extensions."
    );
  }

  public FTPResource select(URI uri, Credential credential) {
    URI endpoint = uri.endpointURI(), resource = uri.resourceURI();
    return new FTPSession(endpoint, credential).select(resource.path());
  }
}
