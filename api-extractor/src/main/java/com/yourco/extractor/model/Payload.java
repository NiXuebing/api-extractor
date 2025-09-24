package com.yourco.extractor.model;

import com.yourco.extractor.types.JavaType;
import com.yourco.extractor.wrapper.WrapperMeta;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Payload {

  private final JavaType javaType;
  private final boolean required;
  private final List<String> mediaTypes;
  private final WrapperMeta wrapperMeta;

  private Payload(Builder builder) {
    this.javaType = builder.javaType;
    this.required = builder.required;
    this.mediaTypes = Collections.unmodifiableList(new ArrayList<>(builder.mediaTypes));
    this.wrapperMeta = builder.wrapperMeta;
  }

  public JavaType getJavaType() {
    return javaType;
  }

  public boolean isRequired() {
    return required;
  }

  public List<String> getMediaTypes() {
    return mediaTypes;
  }

  public WrapperMeta getWrapperMeta() {
    return wrapperMeta;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private JavaType javaType;
    private boolean required;
    private List<String> mediaTypes = new ArrayList<>();
    private WrapperMeta wrapperMeta;

    private Builder() {}

    public Builder javaType(JavaType javaType) {
      this.javaType = javaType;
      return this;
    }

    public Builder required(boolean required) {
      this.required = required;
      return this;
    }

    public Builder mediaTypes(List<String> mediaTypes) {
      this.mediaTypes = new ArrayList<>(Objects.requireNonNullElse(mediaTypes, List.of()));
      return this;
    }

    public Builder wrapperMeta(WrapperMeta wrapperMeta) {
      this.wrapperMeta = wrapperMeta;
      return this;
    }

    public Payload build() {
      Objects.requireNonNull(javaType, "javaType");
      return new Payload(this);
    }
  }
}
