package com.yourco.extractor.types;

import com.github.javaparser.resolution.types.ResolvedArrayType;
import com.github.javaparser.resolution.types.ResolvedPrimitiveType;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class JavaType {

  private final ResolvedType resolvedType;

  private JavaType(ResolvedType resolvedType) {
    this.resolvedType = Objects.requireNonNull(resolvedType, "resolvedType");
  }

  public static JavaType from(ResolvedType resolvedType) {
    return new JavaType(resolvedType);
  }

  public ResolvedType resolved() {
    return resolvedType;
  }

  public boolean isPrimitive() {
    return resolvedType.isPrimitive();
  }

  public boolean isVoid() {
    return resolvedType.isVoid();
  }

  public boolean isArray() {
    return resolvedType.isArray();
  }

  public boolean isReferenceType() {
    return resolvedType.isReferenceType();
  }

  public boolean isWildcard() {
    return resolvedType.isWildcard();
  }

  public ResolvedPrimitiveType asPrimitive() {
    return resolvedType.asPrimitive();
  }

  public JavaType getComponentType() {
    if (!isArray()) {
      throw new IllegalStateException("Not an array type: " + describe());
    }
    ResolvedArrayType arrayType = resolvedType.asArrayType();
    return new JavaType(arrayType.getComponentType());
  }

  public ResolvedReferenceType asReferenceType() {
    return resolvedType.asReferenceType();
  }

  public String describe() {
    return resolvedType.describe();
  }

  public String getQualifiedName() {
    if (isReferenceType()) {
      return resolvedType.asReferenceType().getQualifiedName();
    }
    if (isPrimitive()) {
      return resolvedType.asPrimitive().describe();
    }
    return resolvedType.describe();
  }

  public List<JavaType> getTypeArguments() {
    if (!isReferenceType()) {
      return List.of();
    }
    ResolvedReferenceType referenceType = resolvedType.asReferenceType();
    List<ResolvedType> values = new ArrayList<>(referenceType.typeParametersValues());
    return values.stream().map(JavaType::from).collect(Collectors.toUnmodifiableList());
  }

  public JavaType toErasure() {
    if (!isReferenceType()) {
      return this;
    }
    return new JavaType(resolvedType.asReferenceType().erasure());
  }

  public Optional<JavaType> getFirstTypeArgument() {
    List<JavaType> args = getTypeArguments();
    if (args.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(args.get(0));
  }

  public boolean isTypeVariable() {
    return resolvedType.isTypeVariable();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof JavaType)) {
      return false;
    }
    JavaType other = (JavaType) o;
    return describe().equals(other.describe());
  }

  @Override
  public int hashCode() {
    return describe().hashCode();
  }

  @Override
  public String toString() {
    return describe();
  }
}
