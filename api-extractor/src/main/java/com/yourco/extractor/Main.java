package com.yourco.extractor;

import com.yourco.extractor.model.Endpoint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "api-extractor", mixinStandardHelpOptions = true, version = "0.1.0")
public class Main implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  @Option(names = "--config", required = true, description = "Path to extractor.yml configuration")
  Path configPath;

  @Option(names = "--out", required = true, description = "Output OpenAPI file path")
  Path outPath;

  @Option(names = "--title", description = "OpenAPI title")
  String title;

  @Option(names = "--version", description = "OpenAPI version")
  String version;

  @Option(names = "--log-level", description = "Log level (ERROR, WARN, INFO, DEBUG)")
  String logLevel = "INFO";

  @Override
  public void run() {
    try {
      if (logLevel != null) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel.toLowerCase());
      }
      if (outPath != null) {
        Path parent = outPath.toAbsolutePath().getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
      }
      ExtractorConfig config = ExtractorConfig.load(configPath);
      String docTitle = title != null ? title : config.getDefaultTitle();
      String docVersion = version != null ? version : config.getDefaultVersion();

      ProjectClasspath classpath = ProjectClasspath.from(config);
      ControllerScanner scanner = new ControllerScanner(classpath, config);
      List<Endpoint> endpoints = scanner.scan();
      LOGGER.info("Discovered {} endpoints", endpoints.size());

      SchemaGenerator schemaGenerator = new SchemaGenerator(classpath, config);
      OpenApiBuilder builder = new OpenApiBuilder(docTitle, docVersion, config);
      for (Endpoint endpoint : endpoints) {
        builder.addEndpoint(endpoint, schemaGenerator);
      }
      builder.write(outPath, schemaGenerator);
      LOGGER.info("OpenAPI specification written to {}", outPath);
    } catch (Exception ex) {
      throw new CommandLine.ExecutionException(new CommandLine(this), "Extraction failed", ex);
    }
  }

  public static void main(String[] args) {
    int code = new CommandLine(new Main()).execute(args);
    System.exit(code);
  }
}
