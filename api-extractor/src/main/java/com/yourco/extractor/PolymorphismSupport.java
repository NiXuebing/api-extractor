package com.yourco.extractor;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.yourco.extractor.types.JavaType;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PolymorphismSupport {

  private static final Logger LOGGER = LoggerFactory.getLogger(PolymorphismSupport.class);

  private final ExtractorConfig.PolymorphismConfig config;
  private final SchemaNaming naming;
  private final JacksonSupport jacksonSupport;
  private final JavaParserFacade typeResolver;

  PolymorphismSupport(
      ExtractorConfig.PolymorphismConfig config,
      SchemaNaming naming,
      JacksonSupport jacksonSupport,
      JavaParserFacade typeResolver) {
    this.config = config;
    this.naming = naming;
    this.jacksonSupport = jacksonSupport;
    this.typeResolver = typeResolver;
  }

  Schema<?> apply(
      JavaType type,
      ResolvedReferenceTypeDeclaration declaration,
      Schema<?> schema,
      Function<JavaType, Schema<?>> schemaResolver) {
    if (!isEnabled()) {
      return schema;
    }
    List<SubType> subTypes = findJsonSubTypes(declaration);
    if (subTypes.isEmpty()) {
      return schema;
    }
    ComposedSchema composed = new ComposedSchema();
    if (schema instanceof io.swagger.v3.oas.models.media.ObjectSchema objectSchema) {
      if (objectSchema.getProperties() != null && !objectSchema.getProperties().isEmpty()) {
        composed.addAllOfItem(schema);
      }
    } else {
      composed.addAllOfItem(schema);
    }
    List<Schema<?>> oneOf = new ArrayList<>();
    Map<String, String> mapping = new LinkedHashMap<>();
    for (SubType subType : subTypes) {
      Schema<?> child = schemaResolver.apply(subType.javaType());
      oneOf.add(child);
      String name = naming.schemaName(subType.javaType());
      mapping.put(subType.alias(), "#/components/schemas/" + name);
    }
    composed.setOneOf(oneOf);
    Discriminator discriminator = new Discriminator();
    discriminator.setPropertyName(config.getDiscriminatorProperty());
    if (!mapping.isEmpty()) {
      discriminator.setMapping(mapping);
    }
    composed.setDiscriminator(discriminator);
    return composed;
  }

  private boolean isEnabled() {
    String strategy = config.getDefault();
    if (strategy == null) {
      return false;
    }
    return !strategy.equalsIgnoreCase("flat-common");
  }

  private List<SubType> findJsonSubTypes(ResolvedReferenceTypeDeclaration declaration) {
    Optional<AnnotationExpr> annotation =
        jacksonSupport.findTypeAnnotation(declaration, "JsonSubTypes");
    if (annotation.isEmpty()) {
      return List.of();
    }
    return parseJsonSubTypes(annotation.get());
  }

  private List<SubType> parseJsonSubTypes(AnnotationExpr annotation) {
    List<SubType> result = new ArrayList<>();
    if (annotation.isSingleMemberAnnotationExpr()) {
      parseTypeExpressions(annotation.asSingleMemberAnnotationExpr().getMemberValue(), result);
    } else if (annotation.isNormalAnnotationExpr()) {
      NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
      for (MemberValuePair pair : normal.getPairs()) {
        if (pair.getNameAsString().equals("value")) {
          parseTypeExpressions(pair.getValue(), result);
        }
      }
    }
    return result;
  }

  private void parseTypeExpressions(Expression expression, List<SubType> result) {
    for (Expression expr : flatten(expression)) {
      if (expr.isNormalAnnotationExpr()) {
        parseTypeAnnotation(expr.asNormalAnnotationExpr(), result);
      } else if (expr.isSingleMemberAnnotationExpr()) {
        parseTypeAnnotation(expr.asSingleMemberAnnotationExpr(), result);
      }
    }
  }

  private void parseTypeAnnotation(NormalAnnotationExpr annotation, List<SubType> result) {
    if (!annotation.getName().getIdentifier().equals("Type")) {
      return;
    }
    Optional<JavaType> type = Optional.empty();
    Optional<String> alias = Optional.empty();
    for (MemberValuePair pair : annotation.getPairs()) {
      switch (pair.getNameAsString()) {
        case "value":
          type = resolveClassExpr(pair.getValue());
          break;
        case "name":
          alias = expressionToString(pair.getValue());
          break;
        default:
          break;
      }
    }
    type.ifPresent(t -> result.add(new SubType(t, alias.orElse(simpleAlias(t)))));
  }

  private void parseTypeAnnotation(SingleMemberAnnotationExpr annotation, List<SubType> result) {
    if (!annotation.getName().getIdentifier().equals("Type")) {
      return;
    }
    Optional<JavaType> type = resolveClassExpr(annotation.getMemberValue());
    type.ifPresent(t -> result.add(new SubType(t, simpleAlias(t))));
  }

  private Optional<JavaType> resolveClassExpr(Expression expression) {
    if (expression.isClassExpr()) {
      ClassExpr classExpr = expression.asClassExpr();
      try {
        return Optional.of(JavaType.from(typeResolver.convertToUsage(classExpr.getType())));
      } catch (RuntimeException ex) {
        LOGGER.debug("Failed to resolve subtype {}: {}", classExpr, ex.getMessage());
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

  private List<Expression> flatten(Expression expression) {
    if (expression.isArrayInitializerExpr()) {
      ArrayInitializerExpr array = expression.asArrayInitializerExpr();
      List<Expression> flattened = new ArrayList<>();
      for (Expression element : array.getValues()) {
        flattened.addAll(flatten(element));
      }
      return flattened;
    }
    return List.of(expression);
  }

  private String simpleAlias(JavaType type) {
    if (type.isReferenceType()) {
      String qualified = type.asReferenceType().getQualifiedName();
      int idx = qualified.lastIndexOf('.');
      return idx >= 0 ? qualified.substring(idx + 1) : qualified;
    }
    return type.describe();
  }

  private record SubType(JavaType javaType, String alias) {}
}
