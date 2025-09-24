# API Extractor

This repository contains a Maven module (`api-extractor`) that statically scans Spring MVC
controllers and produces an OpenAPI 3 document without starting the application. The tool is
intended for CI environments where running the full application stack is impractical.

## Building

```bash
mvn -f api-extractor/pom.xml package
```

> The build downloads dependencies from Maven Central. If the CI environment blocks outbound
> network access, configure a mirror or populate the local Maven cache in advance.

## Usage

After packaging, run the CLI with a configuration file:

```bash
java -jar api-extractor/target/api-extractor-*-jar-with-dependencies.jar \
  --config path/to/extractor.yml \
  --out code-openapi.json \
  --title "Project API" \
  --version "1.0.0"
```

The CLI scans the configured source directories, extracts controller endpoints, resolves request
and response payloads, and writes an OpenAPI JSON document to the specified output path.

## Configuration

An example `extractor.yml` is provided under `api-extractor/src/main/resources`. Important
sections include:

- `sourceDirs`: Source directories to scan for controllers.
- `basePackages`: Package prefixes used to limit scanning.
- `wrappers`: Response wrapper types that should be unwrapped when determining payload schemas. Entries can also provide an `asSchema` template to expand wrappers (for example pagination containers) into structured objects.
- `mediaTypeNormalize`: Overrides for `consumes` / `produces` media types.
- `ignore`: Lists of paths and parameter names to exclude from the generated specification.

The generated OpenAPI document can be compared with the contract exported from YApi using the
`ci/api_diff.py` script referenced in the project plan.
