package stork.test;

import org.junit.Test;
import static org.junit.Assert.*;

import stork.cred.*;

/** Tests for Cred. */
public class TestCred {
  @Test
  public void testOAuthEquality() {
    StorkOAuthCred a = new StorkOAuthCred("aaaaaaaaaaaa");
    StorkOAuthCred b = new StorkOAuthCred("aaaaaaaaaaaa");
    StorkOAuthCred c = new StorkOAuthCred("bbbbbbbbbbbb");

    assertEquals("Cred self-equality failed.", a, a);
    assertEquals("Cred equality failed.", a, b);
    assertNotEquals("Cred inequality failed.", a, c);
  }

  @Test
  public void testUserinfo() {
    StorkUserinfo a = new StorkUserinfo("user", "pass");
    StorkUserinfo b = new StorkUserinfo("user", "pass");
    StorkUserinfo c = new StorkUserinfo("aaaaa", "bbbbb");

    assertEquals("Cred username equality failed.", a.username(), "user");
    assertEquals("Cred password equality failed.", a.password(), "pass");

    assertEquals("Cred self-equality failed.", a, a);
    assertEquals("Cred equality failed.", a, b);
    assertNotEquals("Cred inequality failed.", a, c);
  }
}
