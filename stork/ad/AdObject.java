package stork.ad;

import java.util.*;
import java.math.*;
import java.lang.reflect.*;

/**
 * This class represents a value held as a field in an Ad.
 */
public class AdObject implements Comparable<AdObject> {
  Object object;

  // A map of types to methods for converting to that type.
  private static Map<Class<?>, Method> conversionMap =
    new HashMap<Class<?>, Method>();

  // Add a method to the conversion map.
  private static void map(Class<?> c, String m) {
    try {
      conversionMap.put(c, AdObject.class.getMethod(m));
    } catch (Exception e) {
      // Ignore it.
    }
  }

  static {
    map(null,          "asObject");
    map(Object.class,  "asObject");
    map(Ad.class,      "asAd");
    map(String.class,  "asString");
    map(Number.class,  "asNumber");
    map(Integer.class, "asInt");
    map(Double.class,  "asDouble");
    map(Float.class,   "asFloat");
    map(Byte.class,    "asByte");
    map(Long.class,    "asLong");
    map(Short.class,   "asShort");
    map(Boolean.class, "asBoolean");
    map(Character.class, "asChar");
    map(int.class,     "asInt");
    map(double.class,  "asDouble");
    map(float.class,   "asFloat");
    map(byte.class,    "asByte");
    map(long.class,    "asLong");
    map(short.class,   "asShort");
    map(boolean.class, "asBooleanValue");
    map(char.class,    "asChar");
    map(Map.class,     "asMap");
    map(Collection.class, "asList");
  }

  private AdObject(Object o) {
    object = makePrimitive(o);
  }

  // Convert an object to an ad primitive type.
  // FIXME: Aaaa isn't there a better way?
  private static Object makePrimitive(Object o) {
    if (o == null)
      return o;

    Ad.Marshaller m = Ad.findMarshaller(new AdType(o.getClass()));
    if (m != null) try {
      o = m.marshal(o);
      if (o == null)
        return null;
    } catch (Ad.MarshallerDeference e) {
      // Delegate to default handler.
    }

    if (o instanceof String)
      return o;
    if (o instanceof Number)
      return o;
    if (o instanceof Boolean)
      return o;
    if (o instanceof Character)
      return o.toString();
    if (o instanceof Enum)
      return o.toString();
    if (o instanceof Collection)
      return new Ad((Collection) o);
    if (o instanceof Iterable)
      return new Ad((Iterable) o);
    if (o instanceof Map)
      return new Ad((Map) o);
    if (o.getClass().isArray())
      return new Ad(fixArray(o));
    return Ad.marshal(o);
  }

  // Helper method to wrap primitive arrays.
  private static List fixArray(Object o) {
    List l = fixArray2(o);
    return l;
  } private static List fixArray2(final Object array) {
    assert(array.getClass().isArray());
    Class c = array.getClass().getComponentType();

    if (c.isPrimitive()) return new AbstractList() {
      public Object get(int i) { return Array.get(array, i); }
      public int size() { return Array.getLength(array); }
    };

    return Arrays.asList((Object[]) array);
  }

  public static AdObject wrap(Object o) {
    return (o instanceof AdObject) ? (AdObject) o : new AdObject(o);
  }

  public AdObject setObject(Object o) {
    object = o;
    return this;
  }

  public boolean isAd() {
    return object instanceof Ad;
  }

  public boolean isString() {
    return object instanceof String;
  }

  public boolean isNumber() {
    return object instanceof Number;
  }

  /**
   * Determine if a {@code Number} is {@code NaN} or {@code ±Infinity}.
   */
  public boolean isSpecialNumber() {
    if (isNumber()) {
      if (object instanceof Double)
        return Double.isNaN(asDouble()) || Double.isInfinite(asDouble());
      if (object instanceof Float)
        return Float.isNaN(asFloat()) || Float.isInfinite(asFloat());
    } return false;
  }

  public Object asObject() {
    return object;
  }

  public String asString() {
    return (object != null) ? object.toString() : null;
  } public String toString() {
    return (object != null) ? object.toString() : null;
  }

  public Number asNumber() {
    if (object instanceof Number)
      return (Number) object;
    if (object instanceof String)
      return new BigDecimal(object.toString());
    if (object instanceof Boolean)
      return Integer.valueOf(((Boolean)object).booleanValue() ? 1 : 0);
    throw new RuntimeException("cannot convert to number");
  }

  // Primitive numbers.
  public int asInt() {
    return asNumber().intValue();
  } public double asDouble() {
    return asNumber().doubleValue();
  } public float asFloat() {
    return asNumber().floatValue();
  } public byte asByte() {
    return asNumber().byteValue();
  } public long asLong() {
    return asNumber().longValue();
  } public short asShort() {
    return asNumber().shortValue();
  } public char asChar() {
    return asString().charAt(0);
  }

  public Boolean asBoolean() {
    if (object instanceof Boolean)
      return ((Boolean)object);
    if (object instanceof String)
      return Boolean.valueOf(toString().equalsIgnoreCase("true"));
    if (object instanceof Number)
      return Boolean.valueOf(((Number)object).intValue() != 0);
    throw new RuntimeException("cannot convert to boolean from "+object.getClass());
  } public boolean asBooleanValue() {
    return asBoolean().booleanValue();
  }

  public Ad asAd() {
    if (object instanceof Ad)
      return (Ad)object;
    throw new RuntimeException("Cannot marshal "+object.getClass()+" as ad.");
  }

  public Collection<?> asList() {
    return asAd().unmarshalAs(LinkedList.class);
  }

  public Map<?,?> asMap() {
    return asAd().unmarshalAs(HashMap.class);
  }

  public <T> T as(Class<T> c) {
    return c.cast(as(new AdType(c)));
  } protected Object as(AdType t) {
    Class c = t.wrapper();
    Object o = object, uo;
    if (o == null) {
      return null;
    } try {
      // Check if there's an unmarshaller, and delegate if so.
      Ad.Marshaller ma = Ad.findMarshaller(t);
      if (ma != null) try {
        return c.cast(ma.doUnmarshal(this));
      } catch (Ad.MarshallerDeference e) { }
      // Check if it's an array.
      if (t.isArray())
        return asArray(t);
      // Check if it's an enum.
      if (t.isEnum())
        return t.resolveEnum(asString());
      // Check if it's in the conversion map.
      Method cm = conversionMap.get(c);
      if (cm != null)
        return c.cast(cm.invoke(this));
      // Try looking for a likely constructor.
      AdMember m = t.constructor(object.getClass());
      if (m != null)
        return m.construct(object);
      // Try the nullary constructor.
      if (isAd() && (m = t.constructor()) != null)
        return asAd().unmarshal(m.construct(), t);
      // Try unsafe instantiation as a last resort.
      if (isAd() && (uo = AdUnsafe.create(c)) != null)
        return asAd().unmarshal(uo, t);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(c.toString(), e);
    } throw new RuntimeException("Cannot unmarshal as "+t);
  }

  public <C> C asArray(Class<C> c) {
    return c.cast(asArray(new AdType(c)));
  } protected Object asArray(AdType t) {
    assert t.isArray();
    AdType c = t.component();
    Object arr;
    if (object instanceof Ad) {
      Ad ad = asAd();
      arr = c.asArray(ad.size());

      try {
        int i = 0;
        if (ad.isList()) for (AdObject o : ad.list())
          Array.set(arr, i++, o.as(c));
        else if (ad.isMap()) for (AdObject o : ad.map().values())
          Array.set(arr, i++, o.as(c));
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else {
      // Convert to a singleton array.
      arr = c.asArray(1);
      Array.set(arr, 0, as(c));
    } return arr;
  }

  // Check if the enclosed object is of a given type.
  public boolean is(Class<?> c) {
    return c.isInstance(object);
  }

  /**
   * Get the type of the wrapped object.
   */
  public AdType type() {
    return new AdType(object.getClass());
  }

  public boolean equals(Object o) {
    if (o == null)
      return false;
    if (o == this)
      return true;
    if (o instanceof AdObject)
      return object.equals(((AdObject)o).object);
    return false;
  }

  public int hashCode() {
    return object.hashCode();
  }

  // Compare two objects lexicographically.
  public int compareTo(AdObject o) {
    if (this.equals(o))
      return 0;
    if (object instanceof String)
      return asString().compareTo(o.asString());
    if (object instanceof Number)
      return (int) Math.signum(
        asNumber().doubleValue()-o.asNumber().doubleValue());
    if (object instanceof Boolean)
      return asBoolean().compareTo(o.asBoolean());
    if (object instanceof Ad)
      return 0; //return asAd().compareTo(o.asAd());
    return hashCode() - o.hashCode();
  }
}
