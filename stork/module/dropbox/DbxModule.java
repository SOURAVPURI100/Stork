package stork.module.dropbox;

import stork.feather.*;
import stork.module.*;

public class DbxModule extends Module<DbxResource> {
  {
    name("Stork Dbx Module");
    protocols("dropbox");
    description("Experimental Dropbox module.");
  }

  public DbxResource select(URI uri, Credential credential) {
    URI endpoint = uri.endpointURI(), resource = uri.resourceURI();
    return new DbxSession(endpoint, credential).select(resource.path());
  }
}
