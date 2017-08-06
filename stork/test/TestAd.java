package stork.test;

import org.junit.Test;
import static org.junit.Assert.*;

import stork.ad.*;

/** Tests for Ad. */
public class TestAd {
  @Test
  public void testNoInfOrNaN() {
    Number[] ns = {
      Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN,
      Float.POSITIVE_INFINITY,  Float.NEGATIVE_INFINITY,  Float.NaN
    };

    for (final Number n : ns) {
      String s = Ad.marshal(new Object() {
        Number num = n;
      }).toString();
      assertFalse(s.contains("Inf"));
      assertFalse(s.contains("NaN"));
      assertTrue(s.contains("0"));
    }
  }
}
