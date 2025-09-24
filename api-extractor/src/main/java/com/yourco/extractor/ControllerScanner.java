package com.yourco.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedAnnotationDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.yourco.extractor.model.Endpoint;
import com.yourco.extractor.model.EndpointResponse;
import com.yourco.extractor.model.Param;
import com.yourco.extractor.model.ParameterLocation;
import com.yourco.extractor.model.Payload;
import com.yourco.extractor.types.JavaType;
import com.yourco.extractor.types.Types;
import com.yourco.extractor.wrapper.WrapperMeta;
import com.yourco.extractor.wrapper.WrapperStripper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ControllerScanner {

  private static final Logger LOGGER = LoggerFactory.getLogger(ControllerScanner.class);

  private static final Set<String> CONTROLLER_SIMPLE_NAMES = Set.of("RestController", "Controller");
  private static final Set<String> CONTROLLER_FQNS =
      Set.of(
          "org.springframework.web.bind.annotation.RestController",
          "org.springframework.stereotype.Controller");
  private static final Set<String> MAPPING_SIMPLE_NAMES =
      Set.of(
          "RequestMapping",
          "GetMapping",
          "PostMapping",
          "PutMapping",
          "DeleteMapping",
          "PatchMapping");
  private static final Set<String> MAPPING_FQNS =
      Set.of(
          "org.springframework.web.bind.annotation.RequestMapping",
          "org.springframework.web.bind.annotation.GetMapping",
          "org.springframework.web.bind.annotation.PostMapping",
          "org.springframework.web.bind.annotation.PutMapping",
          "org.springframework.web.bind.annotation.DeleteMapping",
          "org.springframework.web.bind.annotation.PatchMapping");
  private static final Map<String, String> DIRECT_HTTP_METHODS =
      Map.of(
          "GetMapping", "GET",
          "PostMapping", "POST",
          "PutMapping", "PUT",
          "DeleteMapping", "DELETE",
          "PatchMapping", "PATCH");
  private static final Set<String> STANDARD_HTTP_METHODS =
      Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD");
  private static final List<String> DEFAULT_JSON_MEDIA = List.of("application/json");
  private static final List<String> FORM_MEDIA_TYPES = List.of("application/x-www-form-urlencoded");
  private static final List<String> MULTIPART_MEDIA_TYPES = List.of("multipart/form-data");
  private static final Set<String> SIMPLE_TYPE_QUALIFIED =
      Set.of(
          "java.lang.String",
          "java.lang.Boolean",
          "java.lang.Integer",
          "java.lang.Long",
          "java.lang.Short",
          "java.lang.Byte",
          "java.lang.Double",
          "java.lang.Float",
          "java.math.BigDecimal",
          "java.math.BigInteger",
          "java.util.UUID",
          "java.time.LocalDate",
          "java.time.LocalDateTime",
          "java.time.LocalTime",
          "java.time.OffsetDateTime",
          "java.time.OffsetTime",
          "java.time.ZonedDateTime",
          "java.time.Instant",
          "java.time.Duration",
          "java.time.Period",
          "java.util.Date",
          "java.sql.Date",
          "java.sql.Timestamp",
          "java.net.URI",
          "java.net.URL");
  private static final Set<String> INFRASTRUCTURE_TYPES =
      Set.of(
          "javax.servlet.http.HttpServletRequest",
          "javax.servlet.http.HttpServletResponse",
          "jakarta.servlet.http.HttpServletRequest",
          "jakarta.servlet.http.HttpServletResponse",
          "javax.servlet.http.HttpSession",
          "jakarta.servlet.http.HttpSession",
          "org.springframework.web.context.request.WebRequest",
          "org.springframework.web.context.request.NativeWebRequest",
          "org.springframework.web.context.request.ServletWebRequest",
          "org.springframework.http.server.ServerHttpRequest",
          "org.springframework.http.server.ServerHttpResponse",
          "org.springframework.security.core.Authentication",
          "java.security.Principal",
          "org.springframework.validation.BindingResult",
          "org.springframework.ui.Model",
          "org.springframework.ui.ModelMap",
          "org.springframework.web.servlet.ModelAndView",
          "org.springframework.http.HttpHeaders");
  private static final Set<String> IGNORED_PARAMETER_ANNOTATIONS =
      Set.of("RequestAttribute", "SessionAttribute");

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
    for (AnnotationExpr annotation : clazz.getAnnotations()) {
      if (isControllerAnnotation(annotation, new HashSet<>())) {
        return true;
      }
    }
    return false;
  }

  private List<Endpoint> extractEndpoints(ClassOrInterfaceDeclaration clazz, String packageName) {
    MappingInfo classMapping = findMapping(clazz).orElse(MappingInfo.empty());
    List<String> classPaths = defaultIfEmpty(classMapping.paths(), List.of(""));
    List<String> classConsumes = defaultIfEmpty(classMapping.consumes(), List.of());
    List<String> classProduces = defaultIfEmpty(classMapping.produces(), List.of());
    Optional<ResponseStatusInfo> classResponseStatus = findResponseStatus(clazz);

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
      List<String> httpMethods = defaultIfEmpty(mapping.methods(), List.of("GET"));
      List<String> methodPaths = defaultIfEmpty(mapping.paths(), List.of(""));
      List<String> consumes = mapping.consumes().isEmpty() ? classConsumes : mapping.consumes();
      List<String> produces = mapping.produces().isEmpty() ? classProduces : mapping.produces();
      List<String> normalizedConsumes = Util.normalizeMediaTypes(consumes, config);
      List<String> normalizedProduces = Util.normalizeMediaTypes(produces, config);

      Payload requestBody = null;
      List<Param> params = new ArrayList<>();

      for (Parameter parameter : method.getParameters()) {
        ParameterDescriptor descriptor = describeParameter(parameter, httpMethods);
        if (descriptor == null) {
          continue;
        }
        if (descriptor.isRequestBody()) {
          if (requestBody != null) {
            LOGGER.debug(
                "Skipping additional request body parameter {} on {}.{}",
                parameter.getNameAsString(),
                clazz.getNameAsString(),
                method.getNameAsString());
            continue;
          }
          JavaType type = descriptor.getJavaType();
          if (type == null) {
            continue;
          }
          List<String> mediaTypes =
              descriptor.getMediaTypes().isEmpty()
                  ? (normalizedConsumes.isEmpty() ? DEFAULT_JSON_MEDIA : normalizedConsumes)
                  : Util.normalizeMediaTypes(descriptor.getMediaTypes(), config);
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

      JavaType rawResponseType = resolveType(method.getType()).orElse(null);
      JavaType payloadType = null;
      WrapperMeta wrapperMeta = null;
      if (rawResponseType != null) {
        payloadType = wrapperStripper.strip(rawResponseType);
        wrapperMeta = wrapperStripper.meta(rawResponseType).orElse(null);
        if (isVoidLike(payloadType)) {
          payloadType = null;
          wrapperMeta = null;
        }
      }
      if (payloadType == null && rawResponseType == null) {
        payloadType = resolveObjectType();
      }
      Payload responsePayload = null;
      if (payloadType != null) {
        List<String> responseMedia =
            normalizedProduces.isEmpty() ? DEFAULT_JSON_MEDIA : normalizedProduces;
        responsePayload =
            Payload.builder()
                .javaType(payloadType)
                .mediaTypes(responseMedia)
                .required(false)
                .wrapperMeta(wrapperMeta)
                .build();
      }

      ResponseStatusInfo responseStatus =
          findResponseStatus(method)
              .orElseGet(() -> classResponseStatus.orElse(ResponseStatusInfo.ok()));

      EndpointResponse endpointResponse =
          EndpointResponse.builder()
              .statusCode(responseStatus.statusCode())
              .payload(responsePayload)
              .description(responseStatus.description())
              .build();

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
                    .responses(List.of(endpointResponse))
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

  private JavaType resolveObjectType() {
    Type objectType = StaticJavaParser.parseType("java.lang.Object");
    return resolveType(objectType)
        .orElseThrow(() -> new IllegalStateException("Unable to resolve java.lang.Object"));
  }

  private ParameterDescriptor describeParameter(Parameter parameter, List<String> httpMethods) {
    if (hasIgnoredAnnotation(parameter)) {
      return null;
    }

    JavaType type = resolveType(parameter.getType()).orElse(null);
    if (type == null || isInfrastructureType(type)) {
      return null;
    }

    ParameterDescriptor descriptor = new ParameterDescriptor();
    descriptor.javaType = type;
    descriptor.name = parameter.getNameAsString();
    descriptor.required = true;
    descriptor.location = ParameterLocation.QUERY;

    Optional<AnnotationExpr> path = findAnnotation(parameter, "PathVariable");
    Optional<AnnotationExpr> matrix = findAnnotation(parameter, "MatrixVariable");
    Optional<AnnotationExpr> requestParam = findAnnotation(parameter, "RequestParam");
    Optional<AnnotationExpr> requestHeader = findAnnotation(parameter, "RequestHeader");
    Optional<AnnotationExpr> cookieValue = findAnnotation(parameter, "CookieValue");
    Optional<AnnotationExpr> requestBody = findAnnotation(parameter, "RequestBody");
    Optional<AnnotationExpr> requestPart = findAnnotation(parameter, "RequestPart");
    Optional<AnnotationExpr> modelAttribute = findAnnotation(parameter, "ModelAttribute");

    if (path.isPresent() || matrix.isPresent()) {
      descriptor.location = ParameterLocation.PATH;
      descriptor.requestBody = false;
      descriptor.required = true;
      applyNamedParameterAttributes(descriptor, path.orElseGet(matrix::get));
    } else if (requestParam.isPresent()) {
      descriptor.location = ParameterLocation.QUERY;
      descriptor.requestBody = false;
      applyNamedParameterAttributes(descriptor, requestParam.get());
    } else if (requestHeader.isPresent()) {
      descriptor.location = ParameterLocation.HEADER;
      descriptor.requestBody = false;
      applyNamedParameterAttributes(descriptor, requestHeader.get());
    } else if (cookieValue.isPresent()) {
      descriptor.location = ParameterLocation.COOKIE;
      descriptor.requestBody = false;
      applyNamedParameterAttributes(descriptor, cookieValue.get());
    } else if (requestBody.isPresent()) {
      descriptor.requestBody = true;
      descriptor.location = null;
      descriptor.required =
          extractBooleanAttribute(requestBody.get(), "required").orElse(true);
    } else if (requestPart.isPresent()) {
      descriptor.requestBody = true;
      descriptor.location = null;
      descriptor.mediaTypes = new ArrayList<>(MULTIPART_MEDIA_TYPES);
      applyNamedParameterAttributes(descriptor, requestPart.get());
    } else if (modelAttribute.isPresent()) {
      descriptor.requestBody = true;
      descriptor.location = null;
      descriptor.mediaTypes = new ArrayList<>(FORM_MEDIA_TYPES);
    } else {
      classifyImplicitParameter(descriptor, type, httpMethods);
    }

    if (descriptor.defaultValue != null && !descriptor.defaultValue.isEmpty()) {
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

  private boolean isControllerAnnotation(AnnotationExpr annotation, Set<String> visited) {
    String identifier = annotation.getName().getIdentifier();
    if (CONTROLLER_SIMPLE_NAMES.contains(identifier)) {
      return true;
    }
    try {
      return isControllerAnnotation(annotation.resolve(), visited);
    } catch (RuntimeException ex) {
      LOGGER.debug("Failed to resolve controller annotation {}: {}", annotation, ex.getMessage());
      return false;
    }
  }

  private boolean isControllerAnnotation(
      ResolvedAnnotationDeclaration resolved, Set<String> visited) {
    String qualified = resolved.getQualifiedName();
    if (!visited.add(qualified)) {
      return false;
    }
    if (CONTROLLER_SIMPLE_NAMES.contains(resolved.getName())
        || CONTROLLER_FQNS.contains(qualified)) {
      return true;
    }
    for (ResolvedAnnotationDeclaration meta : resolved.getAnnotations()) {
      if (isControllerAnnotation(meta, visited)) {
        return true;
      }
    }
    return false;
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

  private boolean isMappingAnnotation(AnnotationExpr annotation) {
    String identifier = annotation.getName().getIdentifier();
    if (MAPPING_SIMPLE_NAMES.contains(identifier)) {
      return true;
    }
    try {
      return isMappingAnnotation(annotation.resolve(), new HashSet<>());
    } catch (RuntimeException ex) {
      LOGGER.debug("Failed to resolve mapping annotation {}: {}", annotation, ex.getMessage());
      return false;
    }
  }

  private boolean isMappingAnnotation(
      ResolvedAnnotationDeclaration resolved, Set<String> visited) {
    String qualified = resolved.getQualifiedName();
    if (!visited.add(qualified)) {
      return false;
    }
    if (MAPPING_SIMPLE_NAMES.contains(resolved.getName()) || MAPPING_FQNS.contains(qualified)) {
      return true;
    }
    for (ResolvedAnnotationDeclaration meta : resolved.getAnnotations()) {
      if (isMappingAnnotation(meta, visited)) {
        return true;
      }
    }
    return false;
  }

  private MappingInfo parseMapping(AnnotationExpr annotation) {
    MappingInfo info = parseMappingInternal(annotation);
    mergeMetaMapping(annotation, info, new HashSet<>());
    if (info.methods().isEmpty()) {
      info.methods.addAll(httpMethodsFromName(annotation.getName().getIdentifier()));
    }
    return info;
  }

  private MappingInfo parseMappingInternal(AnnotationExpr annotation) {
    MappingInfo info = new MappingInfo();
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
    if (info.methods.isEmpty()) {
      info.methods.addAll(httpMethodsFromName(annotation.getName().getIdentifier()));
    }
    return info;
  }

  private void mergeMetaMapping(AnnotationExpr annotation, MappingInfo target, Set<String> visited) {
    try {
      ResolvedAnnotationDeclaration resolved = annotation.resolve();
      mergeMetaMapping(resolved, target, visited);
    } catch (RuntimeException ex) {
      LOGGER.debug("Failed to resolve meta mapping for {}: {}", annotation, ex.getMessage());
    }
  }

  private void mergeMetaMapping(
      ResolvedAnnotationDeclaration resolved, MappingInfo target, Set<String> visited) {
    String qualified = resolved.getQualifiedName();
    if (!visited.add(qualified)) {
      return;
    }
    resolved
        .toAst()
        .ifPresent(
            decl -> {
              NodeList<AnnotationExpr> annotations = decl.getAnnotations();
              for (AnnotationExpr meta : annotations) {
                if (isMappingAnnotation(meta)) {
                  MappingInfo metaInfo = parseMappingInternal(meta);
                  target.methods.addAll(metaInfo.methods());
                  if (target.paths.isEmpty()) {
                    target.paths.addAll(metaInfo.paths());
                  }
                  if (target.consumes.isEmpty()) {
                    target.consumes.addAll(metaInfo.consumes());
                  }
                  if (target.produces.isEmpty()) {
                    target.produces.addAll(metaInfo.produces());
                  }
                  mergeMetaMapping(meta, target, visited);
                }
              }
            });
    for (ResolvedAnnotationDeclaration meta : resolved.getAnnotations()) {
      if (isMappingAnnotation(meta, new HashSet<>())) {
        mergeMetaMapping(meta, target, visited);
      }
    }
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

  private List<String> httpMethodsFromName(String identifier) {
    if (identifier == null) {
      return List.of();
    }
    if (DIRECT_HTTP_METHODS.containsKey(identifier)) {
      return List.of(DIRECT_HTTP_METHODS.get(identifier));
    }
    String upper = identifier.toUpperCase(Locale.ROOT);
    for (String method : STANDARD_HTTP_METHODS) {
      if (upper.startsWith(method)) {
        return List.of(method);
      }
    }
    return List.of();
  }

  private boolean hasIgnoredAnnotation(Parameter parameter) {
    for (AnnotationExpr annotation : parameter.getAnnotations()) {
      if (IGNORED_PARAMETER_ANNOTATIONS.contains(annotation.getName().getIdentifier())) {
        return true;
      }
    }
    return false;
  }

  private Optional<AnnotationExpr> findAnnotation(Parameter parameter, String simpleName) {
    return parameter.getAnnotations().stream()
        .filter(annotation -> annotation.getName().getIdentifier().equals(simpleName))
        .findFirst();
  }

  private void applyNamedParameterAttributes(
      ParameterDescriptor descriptor, AnnotationExpr annotation) {
    descriptor.name = extractStringAttribute(annotation, "value").orElse(descriptor.name);
    descriptor.name = extractStringAttribute(annotation, "name").orElse(descriptor.name);
    descriptor.required =
        extractBooleanAttribute(annotation, "required").orElse(descriptor.required);
    descriptor.defaultValue = extractStringAttribute(annotation, "defaultValue").orElse(null);
  }

  private void classifyImplicitParameter(
      ParameterDescriptor descriptor, JavaType type, List<String> httpMethods) {
    if (isSimpleType(type)) {
      descriptor.location = ParameterLocation.QUERY;
      descriptor.requestBody = false;
      descriptor.required = true;
      return;
    }
    if (!allowsRequestBody(httpMethods)) {
      descriptor.location = ParameterLocation.QUERY;
      descriptor.requestBody = false;
      descriptor.required = false;
      return;
    }
    descriptor.requestBody = true;
    descriptor.location = null;
    descriptor.mediaTypes = new ArrayList<>(FORM_MEDIA_TYPES);
  }

  private boolean allowsRequestBody(List<String> httpMethods) {
    if (httpMethods == null || httpMethods.isEmpty()) {
      return true;
    }
    for (String method : httpMethods) {
      String normalized = method == null ? "" : method.toUpperCase(Locale.ROOT);
      if (!normalized.equals("GET") && !normalized.equals("DELETE") && !normalized.equals("HEAD")) {
        return true;
      }
    }
    return false;
  }

  private boolean isInfrastructureType(JavaType type) {
    if (!type.isReferenceType()) {
      return false;
    }
    return INFRASTRUCTURE_TYPES.contains(type.getQualifiedName());
  }

  private boolean isSimpleType(JavaType type) {
    if (type.isPrimitive()) {
      return true;
    }
    if (!type.isReferenceType()) {
      return false;
    }
    String qualifiedName = type.getQualifiedName();
    if (SIMPLE_TYPE_QUALIFIED.contains(qualifiedName)) {
      return true;
    }
    try {
      Optional<ResolvedReferenceTypeDeclaration> declaration =
          type.asReferenceType().getTypeDeclaration();
      return declaration.isPresent() && declaration.get().isEnum();
    } catch (RuntimeException ex) {
      LOGGER.debug("Failed to inspect type {}: {}", type.describe(), ex.getMessage());
      return false;
    }
  }

  private boolean isVoidLike(JavaType type) {
    if (type == null) {
      return true;
    }
    if (type.isVoid()) {
      return true;
    }
    if (type.isReferenceType() && "java.lang.Void".equals(type.getQualifiedName())) {
      return true;
    }
    return false;
  }

  private Optional<ResponseStatusInfo> findResponseStatus(ClassOrInterfaceDeclaration clazz) {
    return findResponseStatus((NodeWithAnnotations<?>) clazz);
  }

  private Optional<ResponseStatusInfo> findResponseStatus(MethodDeclaration method) {
    return findResponseStatus((NodeWithAnnotations<?>) method);
  }

  private Optional<ResponseStatusInfo> findResponseStatus(NodeWithAnnotations<?> node) {
    for (AnnotationExpr annotation : node.getAnnotations()) {
      if (annotation.getName().getIdentifier().equals("ResponseStatus")) {
        return parseResponseStatus(annotation);
      }
    }
    return Optional.empty();
  }

  private Optional<ResponseStatusInfo> parseResponseStatus(AnnotationExpr annotation) {
    Optional<String> statusCode = Optional.empty();
    Optional<String> reason = Optional.empty();
    if (annotation.isSingleMemberAnnotationExpr()) {
      statusCode = parseStatusCode(annotation.asSingleMemberAnnotationExpr().getMemberValue());
    } else if (annotation.isNormalAnnotationExpr()) {
      NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
      for (MemberValuePair pair : normal.getPairs()) {
        switch (pair.getNameAsString()) {
          case "value":
          case "code":
            statusCode = parseStatusCode(pair.getValue());
            break;
          case "reason":
            reason = expressionToString(pair.getValue());
            break;
          default:
            break;
        }
      }
    }
    if (statusCode.isEmpty()) {
      return Optional.empty();
    }
    String code = statusCode.get();
    String description = reason.orElseGet(() -> HttpStatusLookup.defaultReason(code).orElse(null));
    return Optional.of(new ResponseStatusInfo(code, description));
  }

  private Optional<String> parseStatusCode(Expression expression) {
    if (expression == null) {
      return Optional.empty();
    }
    if (expression.isFieldAccessExpr()) {
      FieldAccessExpr fieldAccess = expression.asFieldAccessExpr();
      String constant = fieldAccess.getName().getIdentifier();
      return HttpStatusLookup.findByConstant(constant).map(entry -> Integer.toString(entry.code()));
    }
    if (expression.isNameExpr()) {
      String constant = expression.asNameExpr().getName().getIdentifier();
      return HttpStatusLookup.findByConstant(constant).map(entry -> Integer.toString(entry.code()));
    }
    if (expression.isIntegerLiteralExpr()) {
      return Optional.of(expression.asIntegerLiteralExpr().getValue());
    }
    if (expression.isMethodCallExpr()) {
      MethodCallExpr call = expression.asMethodCallExpr();
      if (!call.getArguments().isEmpty()) {
        return parseStatusCode(call.getArgument(0));
      }
    }
    if (expression.isEnclosedExpr()) {
      EnclosedExpr enclosed = expression.asEnclosedExpr();
      return parseStatusCode(enclosed.getInner());
    }
    return Optional.empty();
  }

  private static final class ResponseStatusInfo {
    private final String statusCode;
    private final String description;

    ResponseStatusInfo(String statusCode, String description) {
      this.statusCode = statusCode;
      this.description = description;
    }

    static ResponseStatusInfo ok() {
      String reason = HttpStatusLookup.defaultReason("200").orElse("OK");
      return new ResponseStatusInfo("200", reason);
    }

    String statusCode() {
      return statusCode;
    }

    String description() {
      return description;
    }
  }

  private static final class MappingInfo {
    final Set<String> paths = new LinkedHashSet<>();
    final Set<String> methods = new LinkedHashSet<>();
    final Set<String> consumes = new LinkedHashSet<>();
    final Set<String> produces = new LinkedHashSet<>();

    List<String> paths() {
      return new ArrayList<>(paths);
    }

    List<String> methods() {
      return new ArrayList<>(methods);
    }

    List<String> consumes() {
      return new ArrayList<>(consumes);
    }

    List<String> produces() {
      return new ArrayList<>(produces);
    }

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
    private List<String> mediaTypes = new ArrayList<>();

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

    public List<String> getMediaTypes() {
      return new ArrayList<>(mediaTypes);
    }
  }
}
