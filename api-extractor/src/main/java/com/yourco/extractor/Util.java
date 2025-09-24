package com.yourco.extractor;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Util {

  private Util() {}

  public static List<String> normalizeMediaTypes(List<String> raw, ExtractorConfig config) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    return raw.stream()
        .filter(Objects::nonNull)
        .map(s -> config.normalizeMediaType(s.trim()))
        .distinct()
        .collect(Collectors.toList());
  }

  public static boolean matches(Path relative, List<String> patterns) {
    if (patterns == null || patterns.isEmpty()) {
      return false;
    }
    String pathStr = relative.toString().replace('\\', '/');
    for (String pattern : patterns) {
      String normalized = pattern.replace('\\', '/');
      PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalized);
      if (matcher.matches(Path.of(pathStr))) {
        return true;
      }
    }
    return false;
  }

  public static String concatPath(String a, String b) {
    if (a == null || a.isBlank()) {
      return normalizePath(b);
    }
    if (b == null || b.isBlank()) {
      return normalizePath(a);
    }
    String combined = a.endsWith("/") ? a.substring(0, a.length() - 1) : a;
    String second = b.startsWith("/") ? b : "/" + b;
    return normalizePath(combined + second);
  }

  public static String normalizePath(String path) {
    if (path == null || path.isBlank()) {
      return "/";
    }
    String normalized = path.trim();
    if (!normalized.startsWith("/")) {
      normalized = "/" + normalized;
    }
    normalized = normalized.replaceAll("/+", "/");
    if (normalized.length() > 1 && normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  public static List<String> dedupe(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    return new ArrayList<>(values.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList()));
  }

  public static String simpleName(String qualifiedName) {
    if (qualifiedName == null) {
      return null;
    }
    int idx = qualifiedName.lastIndexOf('.');
    return idx >= 0 ? qualifiedName.substring(idx + 1) : qualifiedName;
  }

  public static String toLowerCase(String value) {
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }
}
