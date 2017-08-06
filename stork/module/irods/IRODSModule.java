package stork.module.irods;

import stork.feather.*;
import stork.module.*;

public class IRODSModule extends Module<IRODSResource>{
  { 
    name("Stork IRODS Module");
    protocols("irods");
    description("A module for interacting with iRODS servers.");
  }

  public IRODSResource select(URI uri, Credential credential) {
    URI ep = uri.endpointURI();
    URI re = uri.resourceURI();
    return new IRODSSession(ep, credential).select(re.path());
  }
}
