package com.yourco.extractor;

import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedEnumDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedEnumConstantDeclaration;
import com.github.javaparser.resolution.types.ResolvedPrimitiveType;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.yourco.extractor.types.JavaType;
import com.yourco.extractor.types.Types;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SchemaGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaGenerator.class);

  private final ExtractorConfig config;
  private final Map<String, Schema> components = new LinkedHashMap<>();
  private final Set<String> processing = ConcurrentHashMap.newKeySet();
  private final JacksonSupport jacksonSupport = new JacksonSupport();
  private final BeanValidationSupport validationSupport = new BeanValidationSupport();

  public SchemaGenerator(ProjectClasspath classpath, ExtractorConfig config) {
    this.config = config;
  }

  public Schema<?> toSchema(JavaType type) {
    return toSchema(type, 0);
  }

  public Map<String, Schema> getComponents() {
    return components;
  }

  private Schema<?> toSchema(JavaType type, int depth) {
    if (type == null) {
      return objectSchema();
    }
    if (depth > config.getLimits().getMaxDepth()) {
      LOGGER.warn("Depth limit exceeded for type {}", type.describe());
      return objectSchema();
    }
    if (type.isPrimitive()) {
      return schemaForPrimitive(type.asPrimitive());
    }
    if (type.isArray()) {
      Schema<?> component = toSchema(type.getComponentType(), depth + 1);
      ArraySchema schema = new ArraySchema();
      schema.setItems(component);
      return schema;
    }
    if (Types.isOptional(type)) {
      Optional<JavaType> inner = type.getFirstTypeArgument();
      Schema<?> schema = toSchema(inner.orElse(null), depth + 1);
      schema.setNullable(true);
      return schema;
    }
    if (Types.isCollection(type)) {
      Schema<?> items = toSchema(Types.collectionElementType(type).orElse(null), depth + 1);
      ArraySchema array = new ArraySchema();
      array.setItems(items);
      return array;
    }
    if (Types.isMap(type)) {
      Schema<?> value = toSchema(Types.mapValueType(type).orElse(null), depth + 1);
      MapSchema mapSchema = new MapSchema();
      mapSchema.setAdditionalProperties(value);
      return mapSchema;
    }
    if (type.isReferenceType()) {
      Schema<?> simple = schemaForKnownReference(type);
      if (simple != null) {
        return simple;
      }
      if (type.isTypeVariable() || type.isWildcard()) {
        return objectSchema();
      }
      ResolvedReferenceType ref = type.asReferenceType();
      try {
        Optional<ResolvedReferenceTypeDeclaration> declarationOpt = ref.getTypeDeclaration();
        if (declarationOpt.isEmpty()) {
          return objectSchema();
        }
        ResolvedReferenceTypeDeclaration declaration = declarationOpt.get();
        if (declaration.isEnum()) {
          return registerEnum(type, declaration.asEnum());
        }
        return registerObject(type, declaration, depth + 1);
      } catch (UnsupportedOperationException ex) {
        LOGGER.debug("Unsupported type declaration for {}: {}", type.describe(), ex.getMessage());
        return objectSchema();
      }
    }
    return objectSchema();
  }

  private Schema<?> schemaForPrimitive(ResolvedPrimitiveType primitive) {
    Schema<?> schema = new Schema<>();
    schema.setType(Types.schemaTypeForPrimitive(primitive));
    String format = Types.schemaFormatForPrimitive(primitive);
    if (format != null) {
      schema.setFormat(format);
    }
    return schema;
  }

  private Schema<?> schemaForKnownReference(JavaType type) {
    String qn = type.getQualifiedName();
    switch (qn) {
      case "java.lang.String":
        return new StringSchema();
      case "java.lang.Boolean":
        Schema<?> bool = new Schema<>();
        bool.setType("boolean");
        return bool;
      case "java.lang.Integer":
      case "java.lang.Short":
      case "java.lang.Byte":
        Schema<?> int32 = new Schema<>();
        int32.setType("integer");
        int32.setFormat("int32");
        return int32;
      case "java.lang.Long":
        Schema<?> int64 = new Schema<>();
        int64.setType("integer");
        int64.setFormat("int64");
        return int64;
      case "java.lang.Float":
        Schema<?> floatSchema = new Schema<>();
        floatSchema.setType("number");
        floatSchema.setFormat("float");
        return floatSchema;
      case "java.lang.Double":
      case "java.math.BigDecimal":
        Schema<?> number = new Schema<>();
        number.setType("number");
        number.setFormat("double");
        return number;
      case "java.time.LocalDate":
        Schema<?> date = new Schema<>();
        date.setType("string");
        date.setFormat("date");
        return date;
      case "java.time.LocalDateTime":
      case "java.time.OffsetDateTime":
      case "java.time.Instant":
      case "java.util.Date":
        Schema<?> dateTime = new Schema<>();
        dateTime.setType("string");
        dateTime.setFormat("date-time");
        return dateTime;
      case "java.util.UUID":
        Schema<?> uuid = new Schema<>();
        uuid.setType("string");
        uuid.setFormat("uuid");
        return uuid;
      default:
        return null;
    }
  }

  private Schema<?> registerEnum(JavaType type, ResolvedEnumDeclaration declaration) {
    String name = Types.schemaName(type);
    if (components.containsKey(name)) {
      return referenceSchema(name);
    }
    List<String> values = new ArrayList<>();
    for (ResolvedEnumConstantDeclaration constant : declaration.getEnumConstants()) {
      values.add(constant.getName());
    }
    StringSchema schema = new StringSchema();
    schema.setEnum(values);
    components.put(name, schema);
    return referenceSchema(name);
  }

  private Schema<?> registerObject(
      JavaType type, ResolvedReferenceTypeDeclaration declaration, int depth) {
    String name = Types.schemaName(type);
    if (components.containsKey(name)) {
      return referenceSchema(name);
    }
    if (!processing.add(name)) {
      return referenceSchema(name);
    }
    ObjectSchema schema = new ObjectSchema();
    components.put(name, schema);
    Set<String> required = new LinkedHashSet<>();
    Map<String, Schema> properties = new LinkedHashMap<>();
    Set<String> seen = new LinkedHashSet<>();
    try {
      for (ResolvedFieldDeclaration field : declaration.getAllFields()) {
        if (field.isStatic()) {
          continue;
        }
        String fieldName = field.getName();
        String propertyName =
            jacksonSupport.findSerializedName(field, fieldName).orElse(fieldName);
        if (seen.contains(propertyName)) {
          continue;
        }
        if (jacksonSupport.isIgnored(field)) {
          continue;
        }
        JavaType fieldType = JavaType.from(field.getType());
        Schema<?> propertySchema = toSchema(fieldType, depth + 1);
        properties.put(propertyName, propertySchema);
        seen.add(propertyName);
        validationSupport.apply(propertyName, field, propertySchema, required);
        if (properties.size() >= config.getLimits().getMaxProperties()) {
          LOGGER.warn("Property limit exceeded for {}", name);
          break;
        }
      }
    } finally {
      processing.remove(name);
    }
    if (!properties.isEmpty()) {
      schema.setProperties(properties);
    }
    if (!required.isEmpty()) {
      schema.setRequired(new ArrayList<>(required));
    }
    return referenceSchema(name);
  }

  private Schema<?> referenceSchema(String name) {
    Schema<?> schema = new Schema<>();
    schema.set$ref("#/components/schemas/" + name);
    return schema;
  }

  private Schema<?> objectSchema() {
    Schema<?> schema = new Schema<>();
    schema.setType("object");
    return schema;
  }
}
