package com.yourco.extractor;

import com.yourco.extractor.types.JavaType;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SchemaNaming {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaNaming.class);

  private final ExtractorConfig.NamingConfig config;
  private final Map<String, String> typeToName = new HashMap<>();
  private final Map<String, String> nameToType = new HashMap<>();
  private final Map<String, Integer> counters = new HashMap<>();

  public SchemaNaming(ExtractorConfig.NamingConfig config) {
    this.config = Objects.requireNonNull(config, "naming config");
  }

  public synchronized String schemaName(JavaType type) {
    String typeId = type == null ? "java.lang.Object" : type.describe();
    String existing = typeToName.get(typeId);
    if (existing != null) {
      return existing;
    }
    String baseName = computeBaseName(type);
    String unique = ensureUnique(baseName, typeId);
    typeToName.put(typeId, unique);
    return unique;
  }

  private String computeBaseName(JavaType type) {
    String strategy = config.getSchemaName();
    if (type == null) {
      return "Object";
    }
    if (!type.isReferenceType()) {
      return sanitize(type.describe());
    }
    String normalized = strategy == null ? "" : strategy.toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "FQN" -> sanitize(type.asReferenceType().getQualifiedName());
      case "SIMPLE_NAME" -> sanitize(simpleName(type));
      case "SIMPLE_NAME_WITH_TYPEARGS" -> sanitize(simpleNameWithArgs(type));
      case "FQN_ERASED_WITH_TYPEARGS" -> sanitize(fqnWithArgs(type));
      default -> sanitize(fqnWithArgs(type));
    };
  }

  private String ensureUnique(String baseName, String typeId) {
    String existingType = nameToType.get(baseName);
    if (existingType == null) {
      nameToType.put(baseName, typeId);
      return baseName;
    }
    if (existingType.equals(typeId)) {
      return baseName;
    }
    String collisionStrategy = config.getCollision();
    String normalized = collisionStrategy == null ? "" : collisionStrategy.toLowerCase(Locale.ROOT);
    switch (normalized) {
      case "suffix-number":
        int counter = counters.getOrDefault(baseName, 0);
        String candidate;
        do {
          counter++;
          candidate = baseName + "_" + counter;
        } while (nameToType.containsKey(candidate));
        counters.put(baseName, counter);
        nameToType.put(candidate, typeId);
        return candidate;
      case "error":
        throw new IllegalStateException(
            "Schema name collision for "
                + baseName
                + " between "
                + existingType
                + " and "
                + typeId);
      case "first-wins-log":
      default:
        if (!Objects.equals(existingType, typeId)) {
          LOGGER.warn(
              "Schema name collision for {} between {} and {}",
              baseName,
              existingType,
              typeId);
        }
        return baseName;
    }
  }

  private String fqnWithArgs(JavaType type) {
    StringBuilder sb = new StringBuilder(type.asReferenceType().getQualifiedName());
    List<JavaType> args = type.getTypeArguments();
    if (!args.isEmpty()) {
      sb.append('_');
      for (JavaType arg : args) {
        sb.append(shortName(arg)).append('_');
      }
    }
    return trimUnderscore(sb.toString());
  }

  private String simpleName(JavaType type) {
    return simpleName(type.asReferenceType().getQualifiedName());
  }

  private String simpleName(String qualifiedName) {
    int idx = qualifiedName.lastIndexOf('.');
    return idx >= 0 ? qualifiedName.substring(idx + 1) : qualifiedName;
  }

  private String simpleNameWithArgs(JavaType type) {
    StringBuilder sb = new StringBuilder(simpleName(type));
    List<JavaType> args = type.getTypeArguments();
    if (!args.isEmpty()) {
      sb.append('_');
      for (JavaType arg : args) {
        sb.append(shortName(arg)).append('_');
      }
    }
    return trimUnderscore(sb.toString());
  }

  private String shortName(JavaType type) {
    if (type.isReferenceType()) {
      return simpleName(type.asReferenceType().getQualifiedName());
    }
    return type.describe();
  }

  private String trimUnderscore(String value) {
    if (value.endsWith("_")) {
      return value.substring(0, value.length() - 1);
    }
    return value;
  }

  private String sanitize(String raw) {
    return raw.replace('<', '_')
        .replace('>', '_')
        .replace(',', '_')
        .replace('.', '_')
        .replace(" ", "");
  }
}
