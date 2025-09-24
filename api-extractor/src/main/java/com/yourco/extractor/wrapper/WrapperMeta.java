package com.yourco.extractor.wrapper;

import com.yourco.extractor.types.JavaType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WrapperMeta {

  private final String wrapperType;
  private final JavaType originalType;
  private final Map<String, Object> metadata;
  private final Map<String, Object> schemaTemplate;

  public WrapperMeta(
      String wrapperType,
      JavaType originalType,
      Map<String, Object> metadata,
      Map<String, Object> schemaTemplate) {
    this.wrapperType = Objects.requireNonNull(wrapperType, "wrapperType");
    this.originalType = Objects.requireNonNull(originalType, "originalType");
    this.metadata = copy(metadata);
    this.schemaTemplate = copy(schemaTemplate);
  }

  public String getWrapperType() {
    return wrapperType;
  }

  public JavaType getOriginalType() {
    return originalType;
  }

  public Optional<Map<String, Object>> getMetadata() {
    return Optional.ofNullable(metadata);
  }

  public Optional<Map<String, Object>> getSchemaTemplate() {
    return Optional.ofNullable(schemaTemplate);
  }

  private Map<String, Object> copy(Map<String, Object> source) {
    if (source == null || source.isEmpty()) {
      return null;
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }
}
