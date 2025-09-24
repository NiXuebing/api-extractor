package com.yourco.extractor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserAnnotationDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserEnumDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserInterfaceDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserRecordDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JacksonSupport {

  public boolean isIgnored(ResolvedFieldDeclaration field) {
    return hasAnnotation(field, "com.fasterxml.jackson.annotation.JsonIgnore");
  }

  public Optional<String> findSerializedName(ResolvedFieldDeclaration field, String fallback) {
    Optional<String> name = findJsonPropertyValue(field);
    return name.isPresent() ? name : Optional.ofNullable(fallback);
  }

  public Optional<AnnotationExpr> findTypeAnnotation(
      ResolvedReferenceTypeDeclaration declaration, String simpleName) {
    for (AnnotationExpr annotation : typeAnnotations(declaration)) {
      if (annotation.getName().getIdentifier().equals(simpleName)) {
        return Optional.of(annotation);
      }
    }
    return Optional.empty();
  }

  private boolean hasAnnotation(ResolvedFieldDeclaration field, String target) {
    if (field instanceof JavaParserFieldDeclaration parserField) {
      FieldDeclaration declaration = parserField.getWrappedNode();
      return declaration.getAnnotations().stream()
          .anyMatch(annotation -> annotationMatches(annotation, target));
    }
    return false;
  }

  private boolean annotationMatches(AnnotationExpr annotation, String target) {
    String fullName = annotation.getNameAsString();
    String simpleName = annotation.getName().getIdentifier();
    if (target.equals(fullName) || target.equals(simpleName)) {
      return true;
    }
    return target.endsWith("." + simpleName);
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
        SingleMemberAnnotationExpr single = annotation.asSingleMemberAnnotationExpr();
        if (single.getMemberValue().isStringLiteralExpr()) {
          return Optional.of(single.getMemberValue().asStringLiteralExpr().asString());
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

  private Iterable<AnnotationExpr> typeAnnotations(ResolvedReferenceTypeDeclaration declaration) {
    if (declaration instanceof JavaParserClassDeclaration parser) {
      return new ArrayList<>(parser.getWrappedNode().getAnnotations());
    }
    if (declaration instanceof JavaParserInterfaceDeclaration parser) {
      return new ArrayList<>(parser.getWrappedNode().getAnnotations());
    }
    if (declaration instanceof JavaParserEnumDeclaration parser) {
      return new ArrayList<>(parser.getWrappedNode().getAnnotations());
    }
    if (declaration instanceof JavaParserRecordDeclaration parser) {
      return new ArrayList<>(parser.getWrappedNode().getAnnotations());
    }
    if (declaration instanceof JavaParserAnnotationDeclaration parser) {
      return new ArrayList<>(parser.getWrappedNode().getAnnotations());
    }
    return List.of();
  }
}
