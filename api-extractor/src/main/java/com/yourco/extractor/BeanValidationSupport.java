package com.yourco.extractor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import io.swagger.v3.oas.models.media.Schema;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

public final class BeanValidationSupport {

  public void apply(
      String propertyName, ResolvedFieldDeclaration field, Schema<?> schema, Set<String> required) {
    if (hasAny(field, "javax.validation.constraints.NotNull", "jakarta.validation.constraints.NotNull")) {
      required.add(propertyName);
    }
    if (hasAny(field, "javax.validation.constraints.NotBlank", "jakarta.validation.constraints.NotBlank")) {
      required.add(propertyName);
      if ("string".equals(schema.getType())) {
        schema.setMinLength(1);
      }
    }
    if (hasAny(field, "javax.validation.constraints.NotEmpty", "jakarta.validation.constraints.NotEmpty")) {
      required.add(propertyName);
      if ("array".equals(schema.getType())) {
        schema.setMinItems(1);
      }
    }

    applySize(field, schema);
    applyMin(field, schema);
    applyMax(field, schema);
    applyPattern(field, schema);
  }

  private boolean hasAny(ResolvedFieldDeclaration field, String... annotations) {
    for (String name : annotations) {
      if (field.hasAnnotation(name)) {
        return true;
      }
    }
    return false;
  }

  private void applySize(ResolvedFieldDeclaration field, Schema<?> schema) {
    Optional<FieldDeclaration> ast = toAst(field);
    if (ast.isEmpty()) {
      return;
    }
    Optional<AnnotationExpr> annotation = findAnnotation(ast.get(), "Size");
    if (annotation.isEmpty()) {
      return;
    }
    Integer min = parseIntegerAttribute(annotation.get(), "min");
    Integer max = parseIntegerAttribute(annotation.get(), "max");
    if ("string".equals(schema.getType())) {
      if (min != null) {
        schema.setMinLength(min);
      }
      if (max != null) {
        schema.setMaxLength(max);
      }
    }
    if ("array".equals(schema.getType())) {
      if (min != null) {
        schema.setMinItems(min);
      }
      if (max != null) {
        schema.setMaxItems(max);
      }
    }
  }

  private void applyMin(ResolvedFieldDeclaration field, Schema<?> schema) {
    Optional<FieldDeclaration> ast = toAst(field);
    if (ast.isEmpty()) {
      return;
    }
    Optional<AnnotationExpr> annotation = findAnnotation(ast.get(), "Min");
    if (annotation.isEmpty()) {
      annotation = findAnnotation(ast.get(), "DecimalMin");
    }
    annotation.ifPresent(ann -> setMinimum(schema, parseDecimalAttribute(ann, "value")));
  }

  private void applyMax(ResolvedFieldDeclaration field, Schema<?> schema) {
    Optional<FieldDeclaration> ast = toAst(field);
    if (ast.isEmpty()) {
      return;
    }
    Optional<AnnotationExpr> annotation = findAnnotation(ast.get(), "Max");
    if (annotation.isEmpty()) {
      annotation = findAnnotation(ast.get(), "DecimalMax");
    }
    annotation.ifPresent(ann -> setMaximum(schema, parseDecimalAttribute(ann, "value")));
  }

  private void applyPattern(ResolvedFieldDeclaration field, Schema<?> schema) {
    Optional<FieldDeclaration> ast = toAst(field);
    if (ast.isEmpty()) {
      return;
    }
    Optional<AnnotationExpr> annotation = findAnnotation(ast.get(), "Pattern");
    annotation
        .flatMap(
            ann ->
                parseStringAttribute(ann, "regexp")
                    .or(() -> parseStringAttribute(ann, "value")))
        .ifPresent(schema::setPattern);
  }

  private void setMinimum(Schema<?> schema, BigDecimal value) {
    if (value == null) {
      return;
    }
    schema.setMinimum(value);
  }

  private void setMaximum(Schema<?> schema, BigDecimal value) {
    if (value == null) {
      return;
    }
    schema.setMaximum(value);
  }

  private Optional<FieldDeclaration> toAst(ResolvedFieldDeclaration field) {
    if (field instanceof JavaParserFieldDeclaration parserField) {
      Node node = parserField.getWrappedNode();
      return node.findAncestor(FieldDeclaration.class);
    }
    return Optional.empty();
  }

  private Optional<AnnotationExpr> findAnnotation(FieldDeclaration field, String simpleName) {
    return field.getAnnotations().stream()
        .filter(ann -> ann.getName().getIdentifier().equals(simpleName))
        .findFirst();
  }

  private Integer parseIntegerAttribute(AnnotationExpr annotation, String attr) {
    return parseStringAttribute(annotation, attr).map(this::parseInt).orElse(null);
  }

  private BigDecimal parseDecimalAttribute(AnnotationExpr annotation, String attr) {
    return parseStringAttribute(annotation, attr).map(BigDecimal::new).orElse(null);
  }

  private Optional<String> parseStringAttribute(AnnotationExpr annotation, String attr) {
    if (annotation == null) {
      return Optional.empty();
    }
    if (annotation.isSingleMemberAnnotationExpr()) {
      if (attr.equals("value")) {
        if (annotation
            .asSingleMemberAnnotationExpr()
            .getMemberValue()
            .isStringLiteralExpr()) {
          return Optional.of(
              annotation
                  .asSingleMemberAnnotationExpr()
                  .getMemberValue()
                  .asStringLiteralExpr()
                  .asString());
        }
        return Optional.of(annotation.asSingleMemberAnnotationExpr().getMemberValue().toString());
      }
      return Optional.empty();
    }
    if (annotation.isNormalAnnotationExpr()) {
      NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
      for (MemberValuePair pair : normal.getPairs()) {
        if (pair.getNameAsString().equals(attr)) {
          if (pair.getValue().isStringLiteralExpr()) {
            return Optional.of(pair.getValue().asStringLiteralExpr().asString());
          }
          return Optional.of(pair.getValue().toString());
        }
      }
    }
    return Optional.empty();
  }

  private Integer parseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
