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
import java.util.Optional;

public final class JacksonSupport {

  public boolean isIgnored(ResolvedFieldDeclaration field) {
    return field.hasAnnotation("com.fasterxml.jackson.annotation.JsonIgnore");
  }

  public Optional<String> findSerializedName(ResolvedFieldDeclaration field, String fallback) {
    Optional<String> name = findJsonPropertyValue(field);
    return name.isPresent() ? name : Optional.ofNullable(fallback);
  }

  private Optional<String> findJsonPropertyValue(ResolvedFieldDeclaration field) {
    if (field instanceof JavaParserFieldDeclaration parserField) {
      return findJsonPropertyValue(parserField.getWrappedNode());
    }
    return Optional.empty();
  }

  private Optional<String> findJsonPropertyValue(Node node) {
    Optional<FieldDeclaration> fieldDecl = node.findAncestor(FieldDeclaration.class);
    if (fieldDecl.isEmpty()) {
      return Optional.empty();
    }
    for (AnnotationExpr annotation : fieldDecl.get().getAnnotations()) {
      if (!annotation.getName().getIdentifier().equals("JsonProperty")) {
        continue;
      }
      if (annotation.isSingleMemberAnnotationExpr()) {
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
      }
      if (annotation.isNormalAnnotationExpr()) {
        NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
        for (MemberValuePair pair : normal.getPairs()) {
          if (pair.getNameAsString().equals("value") && pair.getValue().isStringLiteralExpr()) {
            return Optional.of(pair.getValue().asStringLiteralExpr().asString());
          }
        }
      }
    }
    return Optional.empty();
  }
}
