package com.yourco.extractor.wrapper;

import com.yourco.extractor.ExtractorConfig;
import com.yourco.extractor.types.JavaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WrapperStripper {

  private final List<WrapperPattern> patterns;

  public WrapperStripper(ExtractorConfig config) {
    this.patterns = new ArrayList<>();
    for (ExtractorConfig.WrapperConfig wrapper : config.getWrappers()) {
      if (wrapper.getType() != null) {
        patterns.add(new WrapperPattern(wrapper));
      }
    }
  }

  public JavaType strip(JavaType returnType) {
    return unwrap(returnType).payload();
  }

  public Optional<WrapperMeta> meta(JavaType returnType) {
    return Optional.ofNullable(unwrap(returnType).meta());
  }

  private WrapperResult unwrap(JavaType type) {
    JavaType current = type;
    WrapperMeta lastMeta = null;
    boolean changed;
    do {
      changed = false;
      for (WrapperPattern pattern : patterns) {
        Optional<JavaType> payload = pattern.unwrap(current);
        if (payload.isPresent()) {
          lastMeta =
              new WrapperMeta(
                  pattern.rawType(), current, pattern.metadata(), pattern.schemaTemplate());
          current = payload.get();
          changed = true;
          break;
        }
      }
    } while (changed);
    return new WrapperResult(current, lastMeta);
  }

  private static final class WrapperPattern {
    private final ExtractorConfig.WrapperConfig config;
    private final String rawType;
    private final Map<String, Object> metadata;
    private final Map<String, Object> schemaTemplate;

    WrapperPattern(ExtractorConfig.WrapperConfig config) {
      this.config = config;
      this.rawType = parseRawType(config.getType());
      this.metadata = config.getMetadata();
      this.schemaTemplate = config.getAsSchema();
    }

    Optional<JavaType> unwrap(JavaType type) {
      if (!type.isReferenceType()) {
        return Optional.empty();
      }
      String qualifiedName = type.asReferenceType().getQualifiedName();
      if (!qualifiedName.equals(rawType)) {
        return Optional.empty();
      }
      List<JavaType> args = type.getTypeArguments();
      int index = Math.max(0, config.getPayloadArgIndex());
      if (args.isEmpty() || index >= args.size()) {
        return Optional.empty();
      }
      return Optional.of(args.get(index));
    }

    String rawType() {
      return rawType;
    }

    Map<String, Object> metadata() {
      return metadata;
    }

    Map<String, Object> schemaTemplate() {
      return schemaTemplate;
    }

    private static String parseRawType(String type) {
      String trimmed = type.trim();
      int genericStart = trimmed.indexOf('<');
      if (genericStart > 0) {
        return trimmed.substring(0, genericStart);
      }
      return trimmed;
    }
  }

  private record WrapperResult(JavaType payload, WrapperMeta meta) {}
}
