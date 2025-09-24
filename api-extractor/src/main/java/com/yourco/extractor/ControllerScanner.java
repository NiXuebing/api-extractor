package com.yourco.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.yourco.extractor.model.Endpoint;
import com.yourco.extractor.model.Param;
import com.yourco.extractor.model.ParameterLocation;
import com.yourco.extractor.model.Payload;
import com.yourco.extractor.types.JavaType;
import com.yourco.extractor.types.Types;
import com.yourco.extractor.wrapper.WrapperStripper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ControllerScanner {

  private static final Logger LOGGER = LoggerFactory.getLogger(ControllerScanner.class);

  private static final Set<String> MAPPING_ANNOTATIONS =
      Set.of(
          "RequestMapping",
          "GetMapping",
          "PostMapping",
          "PutMapping",
          "DeleteMapping",
          "PatchMapping");

  private final ProjectClasspath classpath;
  private final ExtractorConfig config;
  private final JavaParser parser;
  private final JavaParserFacade typeResolver;
  private final WrapperStripper wrapperStripper;

  public ControllerScanner(ProjectClasspath classpath, ExtractorConfig config) {
    this.classpath = classpath;
    this.config = config;
    this.parser = new JavaParser(classpath.getParserConfiguration());
    this.typeResolver = JavaParserFacade.get(classpath.getTypeSolver());
    this.wrapperStripper = new WrapperStripper(config);
  }

  public List<Endpoint> scan() {
    List<Endpoint> endpoints = new ArrayList<>();
    for (Path sourceDir : config.getSourceDirectories()) {
      try (Stream<Path> stream = Files.walk(sourceDir)) {
        stream
            .filter(Files::isRegularFile)
            .filter(this::isJavaFile)
            .filter(path -> !isExcluded(sourceDir, path))
            .forEach(path -> parseFile(path).ifPresent(endpoints::addAll));
      } catch (IOException ex) {
        LOGGER.warn("Failed to scan directory {}: {}", sourceDir, ex.getMessage());
      }
    }
    return endpoints;
  }

  private boolean isJavaFile(Path path) {
    return path.toString().endsWith(".java");
  }

  private boolean isExcluded(Path sourceDir, Path file) {
    Path relative = sourceDir.relativize(file);
    return Util.matches(relative, config.getExcludes());
  }

  private Optional<List<Endpoint>> parseFile(Path path) {
    try {
      return parser.parse(path).getResult().map(cu -> extractEndpoints(cu, path));
    } catch (IOException | ParseProblemException ex) {
      LOGGER.warn("Failed to parse {}: {}", path, ex.getMessage());
      return Optional.empty();
    }
  }

  private List<Endpoint> extractEndpoints(CompilationUnit cu, Path path) {
    String packageName = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
    if (!config.getBasePackages().isEmpty()
        && config.getBasePackages().stream().noneMatch(packageName::startsWith)) {
      return List.of();
    }
    List<Endpoint> endpoints = new ArrayList<>();
    for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
      if (!clazz.isInterface() && isController(clazz)) {
        endpoints.addAll(extractEndpoints(clazz, packageName));
      }
    }
    return endpoints;
  }

  private boolean isController(ClassOrInterfaceDeclaration clazz) {
    return hasAnnotation(clazz, "RestController") || hasAnnotation(clazz, "Controller");
  }

  private List<Endpoint> extractEndpoints(ClassOrInterfaceDeclaration clazz, String packageName) {
    MappingInfo classMapping = findMapping(clazz).orElse(MappingInfo.empty());
    List<String> classPaths = defaultIfEmpty(classMapping.paths, List.of(""));
    List<String> classConsumes = defaultIfEmpty(classMapping.consumes, List.of());
    List<String> classProduces = defaultIfEmpty(classMapping.produces, List.of());

    List<Endpoint> endpoints = new ArrayList<>();
    for (MethodDeclaration method : clazz.getMethods()) {
      if (!method.isPublic()) {
        continue;
      }
      Optional<MappingInfo> mappingOpt = findMapping(method);
      if (mappingOpt.isEmpty()) {
        continue;
      }
      MappingInfo mapping = mappingOpt.get();
      List<String> httpMethods = defaultIfEmpty(mapping.methods, List.of("GET"));
      List<String> methodPaths = defaultIfEmpty(mapping.paths, List.of(""));
      List<String> consumes = mapping.consumes.isEmpty() ? classConsumes : mapping.consumes;
      List<String> produces = mapping.produces.isEmpty() ? classProduces : mapping.produces;
      List<String> normalizedConsumes = Util.normalizeMediaTypes(consumes, config);
      List<String> normalizedProduces = Util.normalizeMediaTypes(produces, config);

      Payload requestBody = null;
      List<Param> params = new ArrayList<>();

      for (Parameter parameter : method.getParameters()) {
        ParameterDescriptor descriptor = describeParameter(parameter);
        if (descriptor == null) {
          continue;
        }
        if (descriptor.isRequestBody()) {
          JavaType type = descriptor.getJavaType();
          if (type == null) {
            continue;
          }
          List<String> mediaTypes = normalizedConsumes.isEmpty()
              ? List.of("application/json")
              : normalizedConsumes;
          requestBody =
              Payload.builder()
                  .javaType(type)
                  .mediaTypes(mediaTypes)
                  .required(descriptor.isRequired())
                  .build();
        } else {
          if (config.isParameterIgnored(descriptor.getName())) {
            continue;
          }
          JavaType type = descriptor.getJavaType();
          if (type == null) {
            continue;
          }
          params.add(
              Param.builder()
                  .name(descriptor.getName())
                  .location(descriptor.getLocation())
                  .required(descriptor.isRequired())
                  .javaType(type)
                  .defaultValue(descriptor.getDefaultValue())
                  .build());
        }
      }

      JavaType responseType = resolveType(method.getType()).orElse(null);
      List<String> responseMedia = normalizedProduces.isEmpty()
          ? List.of("application/json")
          : normalizedProduces;
      Payload.Builder responseBuilder =
          Payload.builder().mediaTypes(responseMedia).required(false);
      if (responseType != null && !responseType.isVoid()) {
        JavaType payloadType = wrapperStripper.strip(responseType);
        responseBuilder.javaType(payloadType);
        wrapperStripper.meta(responseType).ifPresent(responseBuilder::wrapperMeta);
      }
      Payload responsePayload = responseBuilder.build();

      for (String classPath : classPaths) {
        for (String methodPath : methodPaths) {
          String fullPath = Util.concatPath(classPath, methodPath);
          if (config.isPathIgnored(fullPath)) {
            continue;
          }
          for (String httpMethod : httpMethods) {
            Endpoint endpoint =
                Endpoint.builder()
                    .httpMethod(Types.normalizeHttpMethod(httpMethod))
                    .fullPath(fullPath)
                    .params(params)
                    .requestBody(requestBody)
                    .response(responsePayload)
                    .consumes(normalizedConsumes)
                    .produces(normalizedProduces)
                    .operationId(packageName + "." + method.getNameAsString())
                    .build();
            endpoints.add(endpoint);
          }
        }
      }
    }
    return endpoints;
  }

  private ParameterDescriptor describeParameter(Parameter parameter) {
    List<String> annotationNames =
        parameter.getAnnotations().stream().map(a -> a.getName().getIdentifier()).collect(Collectors.toList());
    boolean isPath = contains(annotationNames, "PathVariable");
    boolean isQuery = contains(annotationNames, "RequestParam");
    boolean isHeader = contains(annotationNames, "RequestHeader");
    boolean isCookie = contains(annotationNames, "CookieValue");
    boolean isBody = contains(annotationNames, "RequestBody");
    if (!(isPath || isQuery || isHeader || isCookie || isBody)) {
      return null;
    }

    JavaType type = resolveType(parameter.getType()).orElse(null);
    if (type == null) {
      return null;
    }

    ParameterDescriptor descriptor = new ParameterDescriptor();
    descriptor.javaType = type;
    descriptor.requestBody = isBody;
    descriptor.required = isPath || isBody || isQuery;
    descriptor.location = ParameterLocation.QUERY;
    descriptor.name = parameter.getNameAsString();
    if (isPath) {
      descriptor.location = ParameterLocation.PATH;
      descriptor.required = true;
    } else if (isQuery) {
      descriptor.location = ParameterLocation.QUERY;
    } else if (isHeader) {
      descriptor.location = ParameterLocation.HEADER;
    } else if (isCookie) {
      descriptor.location = ParameterLocation.COOKIE;
    } else if (isBody) {
      descriptor.location = null;
    }

    parameter
        .getAnnotations()
        .forEach(
            annotation -> {
              String name = annotation.getName().getIdentifier();
              if (name.equals("PathVariable") || name.equals("RequestParam") || name.equals("RequestHeader") || name.equals("CookieValue")) {
                descriptor.name = extractStringAttribute(annotation, "value").orElse(descriptor.name);
                descriptor.name = extractStringAttribute(annotation, "name").orElse(descriptor.name);
                descriptor.required =
                    extractBooleanAttribute(annotation, "required").orElse(descriptor.required);
                descriptor.defaultValue = extractStringAttribute(annotation, "defaultValue").orElse(null);
              }
              if (name.equals("RequestBody")) {
                descriptor.required =
                    extractBooleanAttribute(annotation, "required").orElse(descriptor.required);
              }
            });
    if (descriptor.defaultValue != null) {
      descriptor.required = false;
    }
    return descriptor;
  }

  private Optional<JavaType> resolveType(Type type) {
    try {
      return Optional.of(JavaType.from(typeResolver.convertToUsage(type)));
    } catch (RuntimeException ex) {
      LOGGER.debug("Failed to resolve type {}: {}", type, ex.getMessage());
      return Optional.empty();
    }
  }

  private Optional<MappingInfo> findMapping(MethodDeclaration method) {
    for (AnnotationExpr annotation : method.getAnnotations()) {
      if (isMappingAnnotation(annotation)) {
        return Optional.of(parseMapping(annotation));
      }
    }
    return Optional.empty();
  }

  private Optional<MappingInfo> findMapping(ClassOrInterfaceDeclaration clazz) {
    for (AnnotationExpr annotation : clazz.getAnnotations()) {
      if (isMappingAnnotation(annotation)) {
        return Optional.of(parseMapping(annotation));
      }
    }
    return Optional.empty();
  }

  private boolean hasAnnotation(ClassOrInterfaceDeclaration clazz, String simpleName) {
    return clazz.getAnnotations().stream().anyMatch(a -> a.getName().getIdentifier().equals(simpleName));
  }

  private boolean isMappingAnnotation(AnnotationExpr annotation) {
    String identifier = annotation.getName().getIdentifier();
    return MAPPING_ANNOTATIONS.contains(identifier);
  }

  private MappingInfo parseMapping(AnnotationExpr annotation) {
    MappingInfo info = new MappingInfo();
    String identifier = annotation.getName().getIdentifier();
    if (!identifier.equals("RequestMapping")) {
      info.methods.add(identifier.replace("Mapping", "").toUpperCase());
    }
    if (annotation.isSingleMemberAnnotationExpr()) {
      info.paths.addAll(extractValues(annotation.asSingleMemberAnnotationExpr().getMemberValue()));
    } else if (annotation.isNormalAnnotationExpr()) {
      NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
      for (MemberValuePair pair : normal.getPairs()) {
        String name = pair.getNameAsString();
        switch (name) {
          case "value":
          case "path":
            info.paths.addAll(extractValues(pair.getValue()));
            break;
          case "method":
            info.methods.addAll(extractMethodValues(pair.getValue()));
            break;
          case "consumes":
            info.consumes.addAll(extractValues(pair.getValue()));
            break;
          case "produces":
            info.produces.addAll(extractValues(pair.getValue()));
            break;
          default:
            break;
        }
      }
    }
    return info;
  }

  private List<String> extractMethodValues(Expression value) {
    List<String> values = new ArrayList<>();
    for (Expression expr : flatten(value)) {
      String text = expr.toString();
      int idx = text.lastIndexOf('.');
      if (idx >= 0) {
        text = text.substring(idx + 1);
      }
      values.add(text.toUpperCase());
    }
    return values;
  }

  private List<String> extractValues(Expression value) {
    List<String> values = new ArrayList<>();
    for (Expression expr : flatten(value)) {
      if (expr.isStringLiteralExpr()) {
        values.add(expr.asStringLiteralExpr().asString());
      } else {
        values.add(expr.toString());
      }
    }
    return values;
  }

  private List<Expression> flatten(Expression expression) {
    if (expression.isArrayInitializerExpr()) {
      ArrayInitializerExpr array = expression.asArrayInitializerExpr();
      return array.getValues().stream().flatMap(v -> flatten(v).stream()).collect(Collectors.toList());
    }
    return List.of(expression);
  }

  private Optional<String> extractStringAttribute(AnnotationExpr annotation, String attr) {
    if (annotation.isSingleMemberAnnotationExpr() && attr.equals("value")) {
      return expressionToString(annotation.asSingleMemberAnnotationExpr().getMemberValue());
    }
    if (annotation.isNormalAnnotationExpr()) {
      for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
        if (pair.getNameAsString().equals(attr)) {
          return expressionToString(pair.getValue());
        }
      }
    }
    return Optional.empty();
  }

  private Optional<String> expressionToString(Expression expression) {
    if (expression.isStringLiteralExpr()) {
      return Optional.of(expression.asStringLiteralExpr().asString());
    }
    return Optional.empty();
  }

  private Optional<Boolean> extractBooleanAttribute(AnnotationExpr annotation, String attr) {
    if (annotation.isNormalAnnotationExpr()) {
      for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
        if (pair.getNameAsString().equals(attr)) {
          String value = pair.getValue().toString();
          return Optional.of(Boolean.parseBoolean(value));
        }
      }
    }
    return Optional.empty();
  }

  private static <T> List<T> defaultIfEmpty(List<T> list, List<T> defaultList) {
    return list == null || list.isEmpty() ? defaultList : list;
  }

  private boolean contains(List<String> names, String target) {
    return names.stream().anyMatch(name -> name.equals(target));
  }

  private static final class MappingInfo {
    final List<String> paths = new ArrayList<>();
    final List<String> methods = new ArrayList<>();
    final List<String> consumes = new ArrayList<>();
    final List<String> produces = new ArrayList<>();

    static MappingInfo empty() {
      return new MappingInfo();
    }
  }

  private static final class ParameterDescriptor {
    private String name;
    private boolean required;
    private ParameterLocation location;
    private JavaType javaType;
    private boolean requestBody;
    private String defaultValue;

    public String getName() {
      return name;
    }

    public boolean isRequired() {
      return required;
    }

    public ParameterLocation getLocation() {
      return location;
    }

    public JavaType getJavaType() {
      return javaType;
    }

    public boolean isRequestBody() {
      return requestBody;
    }

    public String getDefaultValue() {
      return defaultValue;
    }
  }
}
