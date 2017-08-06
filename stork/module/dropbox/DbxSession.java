package stork.module.dropbox;

import java.util.*;
import java.io.*;

import com.dropbox.core.*;

import stork.cred.*;
import stork.feather.*;
import stork.feather.errors.*;
import stork.feather.util.*;

public class DbxSession extends Session<DbxSession, DbxResource> {
  private static DbxWebAuth auth;
  private static String authUrl;

  DbxClient client;

  public DbxSession(URI uri, Credential cred) {
    super(uri, cred);
  }

  public DbxResource select(Path path) {
    return new DbxResource(this, path);
  }

  public Bell<DbxSession> initialize() {
    // If an OAuth token is provided, use it.
    if (credential instanceof StorkOAuthCred) {
      StorkOAuthCred oauth = (StorkOAuthCred) credential;
      DbxRequestConfig config =
        new DbxRequestConfig("StorkCloud", Locale.getDefault().toString());
      client = new DbxClient(config, oauth.data());
      return Bell.wrap(this);
    }

    throw new AuthenticationRequired("oauth");
  }
}
