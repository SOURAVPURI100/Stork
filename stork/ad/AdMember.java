package stork.ad;

import java.lang.reflect.*;

/**
 * A wrapper around class members (fields and methods) that encapsulates extra
 * type information and provides methods for setting and resetting access
 * permissions for getting, setting, or invoking members. This class is not
 * threadsafe, and assumes nothing else is messing with the access permissions
 * of the member, so keep that in mind.
 */
class AdMember extends AdType {
  private Member member;
  transient int locks = 0;

  protected AdMember(Member m) {
    super(m);
    if (m instanceof AccessibleObject)
      member = m;
    else
      throw new IllegalArgumentException("invalid member type: "+m);
  }

  // Get the member as a specific type. Throws an exception if it's the
  // wrong type.
  Field field() {
    return Field.class.cast(member);
  } Method method() {
    return Method.class.cast(member);
  } Constructor constructor() {
    return Constructor.class.cast(member);
  }

  public boolean isField() {
    return member instanceof Field;
  } public boolean isMethod() {
    return member instanceof Method;
  } public boolean isConstructor() {
    return member instanceof Constructor;
  }

  private void unlock() {
    locks++;
    ((AccessibleObject) member).setAccessible(true);
  }

  private void lock() {
    if (locks > 0 && --locks == 0)
      ((AccessibleObject) member).setAccessible(false);
  }

  // Get the name of the member.
  protected String name() {
    return member.getName();
  }

  // If this is a field, get the value of the field on the target.
  protected Object get(Object target) {
    try {
      unlock(); return field().get(target);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally { lock(); }
  }

  // If this is a field, set the field on the target object.
  protected void set(Object target, Object value) {
    try {
      unlock(); field().set(target, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally { lock(); }
  }

  // If this is a method, invoke it.
  protected Object invoke(Object target, Object... args) {
    try {
      unlock(); return method().invoke(target, args);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e.getCause());
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally { lock(); }
  }

  // If this is a method or constructor, return the parameter types.
  protected AdType[] parameters() {
    try {
      unlock();
      if (isMethod())
        return AdType.wrap(null, method().getGenericParameterTypes());
      if (isConstructor())
        return AdType.wrap(null, constructor().getGenericParameterTypes());
      return null;
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally { lock(); }
  }

  // If this is a constructor, construct a new instance with the arguments.
  public Object construct(Object... args) {
    try {
      if (isInner())
        args = prependOuterInstance(args);
      unlock();
      return constructor().newInstance(args);
    } catch (Exception e) {
      throw new RuntimeException("Failed to construct: "+this, e);
    } finally { lock(); }
  }

  // Print the member in a way that is similar to its declaration in code.
  public String toString() {
    return super.toString()+" "+name();
  }

  // Check if this member should be ignored for marshalling.
  public boolean ignore() {
    return ignore(member);
  }

  // Static methods to determine whether members should be ignored for
  // purposes of marshalling.
  static boolean ignore(Member m) {
    int mod = m.getModifiers();
    return m.isSynthetic() ||
           Modifier.isTransient(mod) ||
           Modifier.isStatic(mod);
  }
}
