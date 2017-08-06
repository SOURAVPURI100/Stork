package stork.test;

import java.io.*;

import org.junit.Test;
import static org.junit.Assert.*;

import stork.feather.*;
import stork.feather.util.*;

/** Tests for Feather. */
public class TestFeather {
  @Test(timeout = 3000)
  public void testBell() {
    Bell<String> a = new Bell<String>();
    Bell<String> b = a.new Promise();
    a.ring("test");
    assertEquals("Bell value did not propagate.", a.sync(), b.sync());

    try {
      a = new Bell();
      b = a.new Promise();
      a.cancel();
      b.sync();
      fail("Bell failure did not propagate.");
    } catch (Exception e) { }
  }

  @Test(timeout = 700, expected=RuntimeException.class)
  public void testBellDeadline() {
    new Bell().deadline(.5).sync();
  }

  @Test(timeout = 3000)
  public void testBellAsMethod() {
    Bell a = new Bell();
    Bell<String> b = a.as("yes");
    if (b.isDone())
      fail("As-bell rang before parent.");
    a.ring("no");
    String s = b.sync();
    if (!s.equals("yes"))
      fail("As-bell rang with bad value: "+s);
  }

  @Test public void testEmitter() {
    Emitter<String> emitter = new Emitter<String>();
    Bell<String> a, b, c;

    emitter.emit("one");
    a = emitter.get();
    assertEquals("Emit value then get failed.", a.sync(), "one");

    emitter.emit(new Bell<String>("two"));
    a = emitter.get();
    assertEquals("Emit bell then get failed.", a.sync(), "two");

    a = emitter.get();
    emitter.emit("three");
    assertEquals("Get then emit failed.", a.sync(), "three");

    a = emitter.get();
    emitter.emit(new Bell<String>("four"));
    assertEquals("Get then emit bell failed.", a.sync(), "four");

    a = emitter.get();
    b = emitter.get();
    c = emitter.get();
    emitter.emit("one");
    emitter.emit("two");
    emitter.emit("three");
    assertEquals("First queued get failed.", a.sync(), "one");
    assertEquals("Second queued get failed.", b.sync(), "two");
    assertEquals("Third queued get failed.", c.sync(), "three");

    emitter.emit("one");
    emitter.emit("two");
    emitter.emit("three");
    a = emitter.get();
    b = emitter.get();
    c = emitter.get();
    assertEquals("First queued emit failed.", a.sync(), "one");
    assertEquals("Second queued emit failed.", b.sync(), "two");
    assertEquals("Third queued emit failed.", c.sync(), "three");
  }

  @Test(timeout = 3000)
  public void testTapAsInputStream() throws Exception {
    String expect = "This is the expected string.";
    String got;
    Tap tap = Pipes.tapFromString(expect);
    BufferedReader br = new BufferedReader(
      new InputStreamReader(Pipes.asInputStream(tap)));
    tap.start();
    got = br.readLine();

    assertEquals("Read bad string: "+got, got, expect);
  }
}
