package com.yourco.extractor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public final class ExtractorConfig {

  private Path baseDir;
  private String defaultTitle = "Project API";
  private String defaultVersion = "1.0.0";
  private List<String> sourceDirs = new ArrayList<>();
  private List<String> basePackages = new ArrayList<>();
  private List<String> excludes = new ArrayList<>();
  private List<WrapperConfig> wrappers = new ArrayList<>();
  private Map<String, String> mediaTypeNormalize = new LinkedHashMap<>();
  private IgnoreConfig ignore = new IgnoreConfig();
  private LimitsConfig limits = new LimitsConfig();
  private NamingConfig naming = new NamingConfig();
  private PolymorphismConfig polymorphism = new PolymorphismConfig();
  private List<String> classpath = new ArrayList<>();

  public static ExtractorConfig load(Path path) throws IOException {
    Objects.requireNonNull(path, "config path");
    Constructor constructor = new Constructor(ExtractorConfig.class);
    Yaml yaml = new Yaml(constructor);
    ExtractorConfig config;
    try (InputStream in = Files.newInputStream(path)) {
      config = Optional.ofNullable(yaml.load(in)).orElseGet(ExtractorConfig::new);
    }
    config.baseDir = path.toAbsolutePath().getParent();
    config.applyDefaults();
    config.validate();
    return config;
  }

  private void applyDefaults() {
    if (mediaTypeNormalize.isEmpty()) {
      mediaTypeNormalize.put("*/*", "application/json");
    }
  }

  private void validate() throws IOException {
    for (Path dir : getSourceDirectories()) {
      if (!Files.exists(dir)) {
        throw new IOException("Source directory does not exist: " + dir);
      }
    }
  }

  public List<Path> getSourceDirectories() {
    return sourceDirs.stream().map(this::resolve).collect(Collectors.toUnmodifiableList());
  }

  public List<Path> getClasspathEntries() {
    return classpath.stream().map(this::resolve).collect(Collectors.toUnmodifiableList());
  }

  private Path resolve(String value) {
    Path p = Path.of(value);
    if (p.isAbsolute()) {
      return p.normalize();
    }
    if (baseDir == null) {
      return p.normalize();
    }
    return baseDir.resolve(p).normalize();
  }

  public List<String> getBasePackages() {
    return basePackages;
  }

  public List<String> getExcludes() {
    return excludes;
  }

  public List<WrapperConfig> getWrappers() {
    return wrappers;
  }

  public IgnoreConfig getIgnore() {
    return ignore;
  }

  public LimitsConfig getLimits() {
    return limits;
  }

  public NamingConfig getNaming() {
    return naming;
  }

  public PolymorphismConfig getPolymorphism() {
    return polymorphism;
  }

  public String getDefaultTitle() {
    return defaultTitle;
  }

  public String getDefaultVersion() {
    return defaultVersion;
  }

  public Path getBaseDir() {
    return baseDir;
  }

  public String normalizeMediaType(String mediaType) {
    if (mediaType == null) {
      return "application/json";
    }
    String trimmed = mediaType.trim();
    if (mediaTypeNormalize.containsKey(trimmed)) {
      return mediaTypeNormalize.get(trimmed);
    }
    return trimmed;
  }

  public boolean isPathIgnored(String path) {
    return ignore.paths.stream().anyMatch(pattern -> globMatch(path, pattern));
  }

  public boolean isParameterIgnored(String name) {
    return ignore.parameters.contains(name);
  }

  private boolean globMatch(String path, String pattern) {
    String normalized = path.replace('\\', '/');
    String glob = pattern.replace('\\', '/');
    if (glob.endsWith("/**")) {
      String prefix = glob.substring(0, glob.length() - 3);
      return normalized.startsWith(prefix);
    }
    return normalized.equals(glob);
  }

  public void setDefaultTitle(String defaultTitle) {
    this.defaultTitle = defaultTitle;
  }

  public void setTitle(String title) {
    if (title != null) {
      this.defaultTitle = title;
    }
  }

  public void setDefaultVersion(String defaultVersion) {
    this.defaultVersion = defaultVersion;
  }

  public void setVersion(String version) {
    if (version != null) {
      this.defaultVersion = version;
    }
  }

  public void setSourceDirs(List<String> sourceDirs) {
    this.sourceDirs = Optional.ofNullable(sourceDirs).map(ArrayList::new).orElseGet(ArrayList::new);
  }

  public void setBasePackages(List<String> basePackages) {
    this.basePackages = Optional.ofNullable(basePackages).map(ArrayList::new).orElseGet(ArrayList::new);
  }

  public void setExcludes(List<String> excludes) {
    this.excludes = Optional.ofNullable(excludes).map(ArrayList::new).orElseGet(ArrayList::new);
  }

  public void setWrappers(List<WrapperConfig> wrappers) {
    this.wrappers = Optional.ofNullable(wrappers).map(ArrayList::new).orElseGet(ArrayList::new);
  }

  public void setMediaTypeNormalize(Map<String, String> mediaTypeNormalize) {
    this.mediaTypeNormalize = Optional.ofNullable(mediaTypeNormalize).map(LinkedHashMap::new).orElseGet(LinkedHashMap::new);
  }

  public void setIgnore(IgnoreConfig ignore) {
    this.ignore = Optional.ofNullable(ignore).orElseGet(IgnoreConfig::new);
  }

  public void setLimits(LimitsConfig limits) {
    this.limits = Optional.ofNullable(limits).orElseGet(LimitsConfig::new);
  }

  public void setNaming(NamingConfig naming) {
    this.naming = Optional.ofNullable(naming).orElseGet(NamingConfig::new);
  }

  public void setPolymorphism(PolymorphismConfig polymorphism) {
    this.polymorphism = Optional.ofNullable(polymorphism).orElseGet(PolymorphismConfig::new);
  }

  public void setClasspath(List<String> classpath) {
    this.classpath = Optional.ofNullable(classpath).map(ArrayList::new).orElseGet(ArrayList::new);
  }

  public static final class WrapperConfig {
    private String type;
    private int payloadArgIndex;
    private Map<String, Object> metadata;
    private Map<String, Object> asSchema;

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public int getPayloadArgIndex() {
      return payloadArgIndex;
    }

    public void setPayloadArgIndex(int payloadArgIndex) {
      this.payloadArgIndex = payloadArgIndex;
    }

    public Map<String, Object> getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
      this.metadata = metadata;
    }

    public Map<String, Object> getAsSchema() {
      return asSchema;
    }

    public void setAsSchema(Map<String, Object> asSchema) {
      this.asSchema = asSchema;
    }
  }

  public static final class IgnoreConfig {
    private List<String> paths = new ArrayList<>();
    private List<String> parameters = new ArrayList<>();

    public List<String> getPaths() {
      return paths;
    }

    public void setPaths(List<String> paths) {
      this.paths = Optional.ofNullable(paths).map(ArrayList::new).orElseGet(ArrayList::new);
    }

    public List<String> getParameters() {
      return parameters;
    }

    public void setParameters(List<String> parameters) {
      this.parameters = Optional.ofNullable(parameters).map(ArrayList::new).orElseGet(ArrayList::new);
    }
  }

  public static final class LimitsConfig {
    private int maxDepth = 30;
    private int maxProperties = 2000;

    public int getMaxDepth() {
      return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
      this.maxDepth = maxDepth;
    }

    public int getMaxProperties() {
      return maxProperties;
    }

    public void setMaxProperties(int maxProperties) {
      this.maxProperties = maxProperties;
    }
  }

  public static final class NamingConfig {
    private String schemaName = "FQN_ERASED_WITH_TYPEARGS";
    private String collision = "first-wins-log";

    public String getSchemaName() {
      return schemaName;
    }

    public void setSchemaName(String schemaName) {
      this.schemaName = schemaName;
    }

    public String getCollision() {
      return collision;
    }

    public void setCollision(String collision) {
      this.collision = collision;
    }
  }

  public static final class PolymorphismConfig {
    private String defaultStrategy = "flat-common";
    private String discriminatorProperty = "type";

    public String getDefault() {
      return defaultStrategy;
    }

    public void setDefault(String defaultStrategy) {
      this.defaultStrategy = defaultStrategy;
    }

    public String getDiscriminatorProperty() {
      return discriminatorProperty;
    }

    public void setDiscriminatorProperty(String discriminatorProperty) {
      this.discriminatorProperty = discriminatorProperty;
    }
  }
}
