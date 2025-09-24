package com.yourco.extractor;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProjectClasspath {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProjectClasspath.class);

  private final CombinedTypeSolver typeSolver;
  private final ParserConfiguration parserConfiguration;

  private ProjectClasspath(CombinedTypeSolver typeSolver, ParserConfiguration parserConfiguration) {
    this.typeSolver = typeSolver;
    this.parserConfiguration = parserConfiguration;
  }

  public static ProjectClasspath from(ExtractorConfig config) {
    CombinedTypeSolver solver = new CombinedTypeSolver();
    solver.add(new ReflectionTypeSolver(false));

    for (Path sourceDir : config.getSourceDirectories()) {
      LOGGER.debug("Adding source directory: {}", sourceDir);
      solver.add(new JavaParserTypeSolver(sourceDir));
    }
    for (Path jar : config.getClasspathEntries()) {
      try {
        LOGGER.debug("Adding jar: {}", jar);
        solver.add(new JarTypeSolver(jar));
      } catch (IOException ex) {
        LOGGER.warn("Failed to add jar {} to classpath: {}", jar, ex.getMessage());
      }
    }

    ParserConfiguration configuration = new ParserConfiguration();
    configuration.setLanguageLevel(LanguageLevel.JAVA_17);
    configuration.setSymbolResolver(new JavaSymbolSolver(solver));
    configuration.setAttributeComments(false);
    configuration.setStoreTokens(false);
    StaticJavaParser.setConfiguration(configuration);
    JavaParserFacade.get(solver); // warm up
    return new ProjectClasspath(solver, configuration);
  }

  public CombinedTypeSolver getTypeSolver() {
    return typeSolver;
  }

  public ParserConfiguration getParserConfiguration() {
    return parserConfiguration;
  }
}
