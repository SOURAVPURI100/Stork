package stork.ad;

import java.lang.reflect.*;
import java.util.*;

/** A wrapper around Java reflection types. */
public class AdType {
  final transient Type type;
  private transient Class clazz;
  private transient AdType parent;
  protected Object outer;

  public AdType(Member m) {
    this(getMemberType(m));
  } public AdType(Type t) {
    if (t == null)
      throw new NullPointerException();
    type = t;
  }

  // Get the type from an accessible object.
  private static Type getMemberType(Member m) {
    if (m instanceof Field)
      return ((Field)m).getGenericType();
    if (m instanceof Method)
      return ((Method)m).getGenericReturnType();
    if (m instanceof Constructor)
      return ((Constructor)m).getDeclaringClass();
    throw new Error("Invalid member: "+m);
  }

  // Print the wrapped type in a way similar to how it would appear in
  // source code.
  public String toString() {
    String ps = "";
    if (owner() != null)
      ps = owner()+".";
    if (isArray())
      return component()+"[]";
    return ps+clazz().getSimpleName()+genericString();
  } private String genericString() {
    AdType[] g = generics();
    String s = "";
    switch (g.length) {
      default:
        for (int i = 1; i < g.length; i++) s += ","+g[i];
      case 1: return "<"+g[0]+s+">";
      case 0: return s;
    }
  }

  // Really kind of an ugly hacky way to handle interfaces. This whole thing is
  // pending a rewrite, so it's okay for now I guess.
  private Map<Type,Class> canonicalizationMap = new HashMap<Type,Class>() {{
    put(Map.class, HashMap.class);
    put(List.class, LinkedList.class);
    put(Collection.class, LinkedList.class);
    put(Set.class, HashSet.class);
  }};

  // Type Reification
  // ----------------
  // Resolve the raw class type, if possible. Otherwise null.
  protected Class clazz() {
    return (clazz != null) ? clazz : (clazz = clazz(type));
  } private Class clazz(Type type) {
    if (canonicalizationMap.containsKey(type))
      return canonicalizationMap.get(type);
    if (type instanceof Class)
      return (Class) type;
    if (type instanceof ParameterizedType)
      return clazz(((ParameterizedType) type).getRawType());
    if (type instanceof GenericArrayType)
      return reifyArrayType();
    if (type instanceof TypeVariable)
      return reifyTypeVariable((TypeVariable) type);
    if (type instanceof WildcardType)
      return clazz(((WildcardType)type).getUpperBounds()[0]);
    throw new Error(type.getClass().toString());
  }

  // Resolve a type variable against the parent type. Only works on a
  // best-effort basis. Returns upper type bound if the variable can't
  // be resolved.
  private Class reifyTypeVariable(TypeVariable v) {
    if (parent != null)
      return parent.resolveTypeVariable(v);
    return clazz(v.getBounds()[0]);
  }

  // Resolve a type variable against this type. Returns upper type bound
  // if the variable can't be resolved.
  private Class resolveTypeVariable(TypeVariable v) {
    if (type instanceof ParameterizedType) {
      Type t = scope().get(v);
      if (t != null)
        return clazz(t);
    } return clazz(v.getBounds()[0]);
  }

  // Get the raw class of an array type, or null if not an array. Only
  // really useful if the type is a generic array.
  private Class reifyArrayType() {
    return isArray() ? component().arrayType(1) : null;
  }

  // Returns whether the type is an array and thus has a component type.
  protected boolean isArray() {
    return (type instanceof GenericArrayType) || clazz().isArray();
  }

  // Return an array class with a component type of the encapsulated class.
  protected Class arrayType(int dim) {
    AdType t = this;
    if (dim <= 0)
      return clazz();
    return asArray(new int[dim]).getClass();
  }

  // Get the generic parameters of the type. Returns a zero-length array if the
  // type has no generic parameters.
  public AdType[] generics() {
    return wrap(rawGenerics());
  } private Type[] rawGenerics() {
    if (type instanceof ParameterizedType)
      return ((ParameterizedType) type).getActualTypeArguments();
    if (type instanceof Class)
      return ((Class)type).getTypeParameters();
    return new Type[0];
  }

  // Get the type variable scope for this type.
  protected Map<TypeVariable, Type> scope() {
    Map<TypeVariable, Type> scope;
    scope = (parent != null) ?
      parent.scope() : new HashMap<TypeVariable, Type>();
    Type[] types = rawGenerics();
    TypeVariable[] vars = clazz().getTypeParameters();
    assert types.length == vars.length;
    for (int i = 0; i < vars.length; i++)
      scope.put(vars[i], types[i]);
    return scope;
  }

  // Wrap an array of types.
  protected AdType[] wrap(Type[] types) {
    return wrap(this, types);
  }

  protected static AdType[] wrap(AdType parent, Type[] types) {
    AdType[] arr = new AdType[types.length];
    for (int i = 0; i < types.length; i++) {
      arr[i] = new AdType(types[i]);
      if (parent != null) arr[i].parent(parent);
    } return arr;
  }

  // If this an array, get the component type. Otherwise null.
  protected AdType component() {
    Type t = rawComponent();
    return (t != null) ? new AdType(t) : null;
  } private Type rawComponent() {
    if (type instanceof GenericArrayType)
      return ((GenericArrayType)type).getGenericComponentType();
    return clazz().getComponentType();
  }

  // Type Hierarchy Reflection
  // -------------------------
  // Get the superclasses of this type, from most general to most specific (or
  // reversed), including this type. The returned array will contain only this
  // type if the wrapped type is an interface or an array type.
  protected AdType[] supers() {
    return supers(false);
  } protected AdType[] supers(boolean reverse) {
    List<AdType> list = new LinkedList<AdType>();
    return fillSupers(list, reverse).toArray(new AdType[0]);
  } private List<AdType> fillSupers(List<AdType> list, boolean reverse) {
    if (parent != null)
      parent.fillSupers(list, reverse);
    else if (rawSuper() != null)
      superclass().fillSupers(list, reverse);
    if (reverse)
      list.add(0, this);
    else
      list.add(this);
    return list;
  }

  // Get the super class of this type, or null if it's an interface or array
  // type.
  public AdType superclass() {
    Type t = rawSuper();
    if (t == null)
      return null;
    return new AdType(t);
  } private Type rawSuper() {
    if (isArray())
      return null;
    return clazz().getGenericSuperclass();
  }

  // Get the owner type of this type, or null if there is none.
  protected AdType owner() {
    Type o = rawOwner();
    return (o != null) ? new AdType(o) : null;
  } private Type rawOwner() {
    if (type instanceof ParameterizedType)
      return ((ParameterizedType) type).getOwnerType();
    return clazz().getEnclosingClass();
  }

  // Set the parent type, for more accurate type reification.
  protected AdType parent(AdType p) {
    parent = p;
    return this;
  }

  // Member Reflection
  // -----------------
  // Get the fields from the class as a mapping from their names to their
  // reflective field objects.
  protected Map<String, AdMember> fields() {
    Class<?> clazz = clazz();
    if (clazz.equals(Object.class) || clazz.isInterface())
      return new HashMap<String, AdMember>();
    Map<String, AdMember> fields = superclass().fields();
    for (Field f : clazz.getDeclaredFields())
      fields.put(f.getName(), (AdMember) new AdMember(f).parent(this));
    return fields;
  }

  // Get a single field.
  protected AdMember field(String name) {
    Field f = rawField(name);
    if (f == null) return null;
    return (AdMember) new AdMember(f).parent(this);
  } private Field rawField(String name) {
    try {
      return clazz().getDeclaredField(name);
    } catch (Exception e) { } try {
      return clazz().getField(name);
    } catch (Exception e) {
      return null;
    }
  }

  protected Class[] prependOuterClass(Class... params) {
    List<Class> list = new LinkedList<Class>();
    list.add(owner().clazz());
    list.addAll(Arrays.asList(params));
    return list.toArray(new Class[params.length]);
  }

  protected Object[] prependOuterInstance(Object... args) {
    List<Object> list = new LinkedList<Object>();
    list.add(outer);
    list.addAll(Arrays.asList(args));
    return list.toArray(new Object[args.length]);
  }

  // Get a constructor for the class based on the parameter type, or null if
  // none exists.
  protected AdMember constructor(Class... params) {
    try {
      if (isInner())
        params = prependOuterClass(params);
      AdMember m = new AdMember(clazz().getDeclaredConstructor(params));
      m.outer(outer);
      return m;
    } catch (Exception e) {
      return null;
    }
  }

  /** Attempt to construct an instance of the wrapped type. */
  public Object construct(Object... args) {
    if (isInner())
      args = prependOuterInstance(args);
    Class[] params = new Class[args.length];
    for (int i = 0; i < args.length; i++)
      params[i] = (args[i] != null) ? args[i].getClass() : Object.class;
    return constructor(params).construct(args);
  }

  // Get a method based on the parameter type.
  protected AdMember method(String name, Class... params) {
    Method m = rawMethod(name, params);
    if (m == null)
      return null;
    return new AdMember(m);
  } protected Method rawMethod(String name, Class... params) {
    try {
      Method m = clazz().getDeclaredMethod(name, params);
      if (!AdMember.ignore(m)) return m;
    } catch (NoSuchMethodException e) { }

    try {
      Method m = clazz().getMethod(name, params);
      if (!AdMember.ignore(m)) return m;
    } catch (NoSuchMethodException e) { }

    return null;
  }

  // Get all the methods exposed by this class.
  protected AdMember[] methods() {
    try {
      Set<AdMember> methods = new HashSet<AdMember>();
      for (Method m : clazz().getDeclaredMethods()) if (!AdMember.ignore(m))
        methods.add(new AdMember(m));
      for (Method m : clazz().getMethods()) if (!AdMember.ignore(m))
        methods.add(new AdMember(m));
      return methods.toArray(new AdMember[methods.size()]);
    } catch (Exception e) {
      return null;
    }
  }

  // Get all the methods of a given name exposed by this class.
  protected AdMember[] methods(String name) {
    try {
      Set<AdMember> methods = new HashSet<AdMember>();
      for (Method m : clazz().getDeclaredMethods()) if (!AdMember.ignore(m))
        if (name.equals(m.getName())) methods.add(new AdMember(m));
      for (Method m : clazz().getMethods()) if (!AdMember.ignore(m))
        if (name.equals(m.getName())) methods.add(new AdMember(m));
      return methods.toArray(new AdMember[methods.size()]);
    } catch (Exception e) {
      return null;
    }
  }

  // Returns whether the type is a non-static inner type and thus requires an
  // enclosing instance of the parent class.
  protected boolean isInner() {
    return clazz().isMemberClass();
  }

  // Misc
  // ----
  // Check if this type is a primitive.
  protected boolean isPrimitive() {
    return clazz().isPrimitive();
  }

  // Takes care of those nasty primitive type classes.
  protected Class wrapper() {
    return wrapper(clazz());
  } private static Class wrapper(Class c) {
    if (c.isPrimitive()) {
      if (c == int.class)     return Integer.class;
      if (c == long.class)    return Long.class;
      if (c == double.class)  return Double.class;
      if (c == boolean.class) return Boolean.class;
      if (c == float.class)   return Float.class;
      if (c == char.class)    return Character.class;
      if (c == byte.class)    return Byte.class;
      if (c == short.class)   return Short.class;
      if (c == void.class)    return Void.class;
    } return c;
  }

  // Set the reference to an instance of the outer class.
  public void outer(Object outer) { this.outer = outer; }

  // Return a new array instance of this type.
  protected Object asArray(int... dims) {
    if (dims == null || dims.length == 0)
      throw new IllegalArgumentException();
    return Array.newInstance(clazz(), dims);
  }

  // Check if the type is an enum.
  protected boolean isEnum() {
    return clazz().isEnum();
  }

  // If this is an enum, look it up by a string value. Returns null if not
  // found or type is not an enum.
  public Enum resolveEnum(String s) {
    try {
      return Enum.valueOf(clazz(), s);
    } catch (Exception e) {
      return null;
    }
  }
}
