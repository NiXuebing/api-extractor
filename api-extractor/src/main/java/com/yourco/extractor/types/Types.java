package com.yourco.extractor.types;

import com.github.javaparser.resolution.types.ResolvedPrimitiveType;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class Types {

  private Types() {}

  public static boolean isCollection(JavaType type) {
    if (type.isArray()) {
      return true;
    }
    if (!type.isReferenceType()) {
      return false;
    }
    ResolvedReferenceType ref = type.asReferenceType();
    return ref.getQualifiedName().equals("java.util.Collection")
        || ref.getQualifiedName().equals("java.util.List")
        || ref.getQualifiedName().equals("java.util.Set")
        || implementsInterface(ref, "java.util.Collection");
  }

  public static boolean isMap(JavaType type) {
    if (!type.isReferenceType()) {
      return false;
    }
    ResolvedReferenceType ref = type.asReferenceType();
    return ref.getQualifiedName().equals("java.util.Map")
        || implementsInterface(ref, "java.util.Map");
  }

  private static boolean implementsInterface(ResolvedReferenceType ref, String iface) {
    return ref.getAllAncestors().stream()
        .filter(ResolvedReferenceType::isInterface)
        .anyMatch(ancestor -> ancestor.getQualifiedName().equals(iface));
  }

  public static Optional<JavaType> collectionElementType(JavaType type) {
    if (type.isArray()) {
      return Optional.of(type.getComponentType());
    }
    if (!type.isReferenceType()) {
      return Optional.empty();
    }
    List<JavaType> typeArguments = type.getTypeArguments();
    if (!typeArguments.isEmpty()) {
      return Optional.of(typeArguments.get(0));
    }
    return Optional.empty();
  }

  public static Optional<JavaType> mapValueType(JavaType type) {
    if (!isMap(type)) {
      return Optional.empty();
    }
    List<JavaType> typeArguments = type.getTypeArguments();
    if (typeArguments.size() >= 2) {
      return Optional.of(typeArguments.get(1));
    }
    return Optional.empty();
  }

  public static boolean isOptional(JavaType type) {
    return type.isReferenceType()
        && type.asReferenceType().getQualifiedName().equals("java.util.Optional");
  }

  public static String schemaTypeForPrimitive(ResolvedPrimitiveType primitive) {
    switch (primitive.getBoxTypeQName()) {
      case "java.lang.Boolean":
        return "boolean";
      case "java.lang.Byte":
      case "java.lang.Short":
      case "java.lang.Integer":
      case "java.lang.Long":
        return "integer";
      case "java.lang.Float":
      case "java.lang.Double":
        return "number";
      case "java.lang.Character":
        return "string";
      default:
        return "string";
    }
  }

  public static String schemaFormatForPrimitive(ResolvedPrimitiveType primitive) {
    switch (primitive.getBoxTypeQName()) {
      case "java.lang.Byte":
        return "int32";
      case "java.lang.Short":
        return "int32";
      case "java.lang.Integer":
        return "int32";
      case "java.lang.Long":
        return "int64";
      case "java.lang.Float":
        return "float";
      case "java.lang.Double":
        return "double";
      default:
        return null;
    }
  }

  public static String schemaName(JavaType type) {
    if (!type.isReferenceType()) {
      return sanitize(type.describe());
    }
    ResolvedReferenceType ref = type.asReferenceType();
    StringBuilder sb = new StringBuilder(ref.getQualifiedName());
    List<JavaType> args = type.getTypeArguments();
    if (!args.isEmpty()) {
      sb.append('_');
      for (JavaType arg : args) {
        sb.append(shortName(arg)).append('_');
      }
    }
    return sanitize(sb.toString());
  }

  private static String shortName(JavaType type) {
    if (type.isReferenceType()) {
      String qn = type.asReferenceType().getQualifiedName();
      int idx = qn.lastIndexOf('.');
      return idx >= 0 ? qn.substring(idx + 1) : qn;
    }
    return type.describe();
  }

  private static String sanitize(String raw) {
    return raw.replace('<', '_')
        .replace('>', '_')
        .replace(',', '_')
        .replace('.', '_')
        .replace(" ", "");
  }

  public static String normalizeHttpMethod(String method) {
    return method == null ? null : method.toUpperCase(Locale.ROOT);
  }
}
