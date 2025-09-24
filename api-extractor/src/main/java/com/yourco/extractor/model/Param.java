package com.yourco.extractor.model;

import com.yourco.extractor.types.JavaType;
import java.util.Objects;

public class Param {

  private final String name;
  private final ParameterLocation location;
  private final boolean required;
  private final JavaType javaType;
  private final String description;
  private final String defaultValue;

  private Param(Builder builder) {
    this.name = builder.name;
    this.location = builder.location;
    this.required = builder.required;
    this.javaType = builder.javaType;
    this.description = builder.description;
    this.defaultValue = builder.defaultValue;
  }

  public String getName() {
    return name;
  }

  public ParameterLocation getLocation() {
    return location;
  }

  public boolean isRequired() {
    return required;
  }

  public JavaType getJavaType() {
    return javaType;
  }

  public String getDescription() {
    return description;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String name;
    private ParameterLocation location;
    private boolean required;
    private JavaType javaType;
    private String description;
    private String defaultValue;

    private Builder() {}

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder location(ParameterLocation location) {
      this.location = location;
      return this;
    }

    public Builder required(boolean required) {
      this.required = required;
      return this;
    }

    public Builder javaType(JavaType javaType) {
      this.javaType = javaType;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder defaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
      return this;
    }

    public Param build() {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(javaType, "javaType");
      return new Param(this);
    }
  }
}
