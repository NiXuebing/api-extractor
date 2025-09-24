package com.yourco.extractor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yourco.extractor.model.Endpoint;
import com.yourco.extractor.model.Param;
import com.yourco.extractor.model.ParameterLocation;
import com.yourco.extractor.model.Payload;
import com.yourco.extractor.wrapper.WrapperMeta;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.Components;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OpenApiBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiBuilder.class);
  private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final OpenAPI openApi;
  private final ExtractorConfig config;

  public OpenApiBuilder(String title, String version, ExtractorConfig config) {
    this.config = config;
    this.openApi = new OpenAPI();
    this.openApi.setInfo(new Info().title(title).version(version));
    this.openApi.setPaths(new Paths());
  }

  public void addEndpoint(Endpoint endpoint, SchemaGenerator generator) {
    Paths paths = openApi.getPaths();
    if (paths == null) {
      paths = new Paths();
      openApi.setPaths(paths);
    }
    String path = endpoint.getFullPath();
    PathItem pathItem = paths.computeIfAbsent(path, p -> new PathItem());
    Operation operation = new Operation();
    operation.setOperationId(endpoint.getOperationId());

    for (Param param : endpoint.getParams()) {
      Parameter parameter = new Parameter();
      parameter.setName(param.getName());
      parameter.setRequired(param.isRequired());
      parameter.setIn(locationToString(param.getLocation()));
      Schema<?> schema = generator.toSchema(param.getJavaType());
      parameter.setSchema(schema);
      if (param.getDefaultValue() != null) {
        parameter.setExample(param.getDefaultValue());
      }
      operation.addParametersItem(parameter);
    }

    if (endpoint.getRequestBody() != null) {
      operation.setRequestBody(createRequestBody(endpoint.getRequestBody(), generator));
    }

    ApiResponses responses = new ApiResponses();
    responses.addApiResponse("200", createResponse(endpoint.getResponse(), generator));
    operation.setResponses(responses);

    try {
      PathItem.HttpMethod method = PathItem.HttpMethod.valueOf(endpoint.getHttpMethod().toUpperCase(Locale.ROOT));
      pathItem.operation(method, operation);
    } catch (IllegalArgumentException ex) {
      LOGGER.warn("Unsupported HTTP method {} for path {}", endpoint.getHttpMethod(), path);
    }
  }

  private RequestBody createRequestBody(Payload payload, SchemaGenerator generator) {
    RequestBody requestBody = new RequestBody();
    requestBody.setRequired(payload.isRequired());
    requestBody.setContent(createContent(payload, generator));
    return requestBody;
  }

  private ApiResponse createResponse(Payload payload, SchemaGenerator generator) {
    ApiResponse response = new ApiResponse();
    response.setDescription("OK");
    response.setContent(createContent(payload, generator));
    return response;
  }

  private Content createContent(Payload payload, SchemaGenerator generator) {
    Content content = new Content();
    if (payload == null) {
      MediaType mt = new MediaType();
      Schema<?> schema = new Schema<>();
      schema.setType("object");
      mt.setSchema(schema);
      content.addMediaType("application/json", mt);
      return content;
    }
    List<String> mediaTypes = payload.getMediaTypes();
    if (mediaTypes == null || mediaTypes.isEmpty()) {
      mediaTypes = List.of("application/json");
    }
    for (String mediaType : mediaTypes) {
      String normalized = config.normalizeMediaType(mediaType);
      Schema<?> schema = schemaForPayload(payload, generator);
      MediaType mt = new MediaType();
      mt.setSchema(schema);
      content.addMediaType(normalized, mt);
    }
    return content;
  }

  private Schema<?> schemaForPayload(Payload payload, SchemaGenerator generator) {
    Schema<?> inner = generator.toSchema(payload.getJavaType());
    WrapperMeta meta = payload.getWrapperMeta();
    if (meta == null) {
      return inner;
    }
    Optional<Map<String, Object>> template = meta.getSchemaTemplate();
    if (template.isEmpty()) {
      return inner;
    }
    return applyWrapperTemplate(meta, template.get(), inner);
  }

  @SuppressWarnings("unchecked")
  private Schema<?> applyWrapperTemplate(
      WrapperMeta meta, Map<String, Object> template, Schema<?> inner) {
    if (template == null || template.isEmpty()) {
      return inner;
    }
    try {
      Map<String, Object> templateCopy = SCHEMA_MAPPER.convertValue(template, MAP_TYPE);
      Map<String, Object> innerMap = SCHEMA_MAPPER.convertValue(inner, MAP_TYPE);
      Object merged = replaceTemplatePlaceholders(templateCopy, innerMap);
      if (merged instanceof Map<?, ?> mergedMap) {
        return SCHEMA_MAPPER.convertValue(mergedMap, Schema.class);
      }
    } catch (IllegalArgumentException ex) {
      LOGGER.warn(
          "Failed to apply wrapper schema for {}: {}", meta.getWrapperType(), ex.getMessage());
    }
    return inner;
  }

  private Object replaceTemplatePlaceholders(Object node, Map<String, Object> inner) {
    if (node instanceof Map<?, ?> rawMap) {
      Map<String, Object> map = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
        map.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      if (map.size() == 1 && map.containsKey("$ref") && isTemplatePlaceholder(map.get("$ref"))) {
        return deepCopy(inner);
      }
      Map<String, Object> replaced = new LinkedHashMap<>();
      for (Map.Entry<String, Object> entry : map.entrySet()) {
        replaced.put(entry.getKey(), replaceTemplatePlaceholders(entry.getValue(), inner));
      }
      return replaced;
    }
    if (node instanceof List<?> list) {
      List<Object> replaced = new ArrayList<>(list.size());
      for (Object element : list) {
        replaced.add(replaceTemplatePlaceholders(element, inner));
      }
      return replaced;
    }
    if (isTemplatePlaceholder(node)) {
      return deepCopy(inner);
    }
    return node;
  }

  private Object deepCopy(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        copy.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
      }
      return copy;
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>(list.size());
      for (Object element : list) {
        copy.add(deepCopy(element));
      }
      return copy;
    }
    return value;
  }

  private boolean isTemplatePlaceholder(Object value) {
    return value instanceof String && "T".equals(value);
  }

  public void write(Path output, SchemaGenerator generator) throws IOException {
    Components components = new Components();
    components.setSchemas(generator.getComponents());
    openApi.setComponents(components);
    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    try (Writer writer = Files.newBufferedWriter(output)) {
      mapper.writeValue(writer, openApi);
    }
  }

  private String locationToString(ParameterLocation location) {
    return switch (location) {
      case PATH -> "path";
      case QUERY -> "query";
      case HEADER -> "header";
      case COOKIE -> "cookie";
    };
  }
}
