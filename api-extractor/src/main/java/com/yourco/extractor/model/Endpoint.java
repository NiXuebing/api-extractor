package com.yourco.extractor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Endpoint {

  private final String httpMethod;
  private final String fullPath;
  private final List<Param> params;
  private final Payload requestBody;
  private final List<EndpointResponse> responses;
  private final List<String> consumes;
  private final List<String> produces;
  private final String operationId;

  private Endpoint(Builder builder) {
    this.httpMethod = builder.httpMethod;
    this.fullPath = builder.fullPath;
    this.params = Collections.unmodifiableList(new ArrayList<>(builder.params));
    this.requestBody = builder.requestBody;
    this.responses = Collections.unmodifiableList(new ArrayList<>(builder.responses));
    this.consumes = Collections.unmodifiableList(new ArrayList<>(builder.consumes));
    this.produces = Collections.unmodifiableList(new ArrayList<>(builder.produces));
    this.operationId = builder.operationId;
  }

  public String getHttpMethod() {
    return httpMethod;
  }

  public String getFullPath() {
    return fullPath;
  }

  public List<Param> getParams() {
    return params;
  }

  public Payload getRequestBody() {
    return requestBody;
  }

  public List<EndpointResponse> getResponses() {
    return responses;
  }

  public List<String> getConsumes() {
    return consumes;
  }

  public List<String> getProduces() {
    return produces;
  }

  public String getOperationId() {
    return operationId;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String httpMethod;
    private String fullPath;
    private List<Param> params = new ArrayList<>();
    private Payload requestBody;
    private List<EndpointResponse> responses = new ArrayList<>();
    private List<String> consumes = new ArrayList<>();
    private List<String> produces = new ArrayList<>();
    private String operationId;

    private Builder() {}

    public Builder httpMethod(String httpMethod) {
      this.httpMethod = httpMethod;
      return this;
    }

    public Builder fullPath(String fullPath) {
      this.fullPath = fullPath;
      return this;
    }

    public Builder params(List<Param> params) {
      this.params = new ArrayList<>(Objects.requireNonNullElse(params, List.of()));
      return this;
    }

    public Builder addParam(Param param) {
      this.params.add(param);
      return this;
    }

    public Builder requestBody(Payload requestBody) {
      this.requestBody = requestBody;
      return this;
    }

    public Builder responses(List<EndpointResponse> responses) {
      this.responses = new ArrayList<>(Objects.requireNonNullElse(responses, List.of()));
      return this;
    }

    public Builder addResponse(EndpointResponse response) {
      if (response != null) {
        this.responses.add(response);
      }
      return this;
    }

    public Builder consumes(List<String> consumes) {
      this.consumes = new ArrayList<>(Objects.requireNonNullElse(consumes, List.of()));
      return this;
    }

    public Builder produces(List<String> produces) {
      this.produces = new ArrayList<>(Objects.requireNonNullElse(produces, List.of()));
      return this;
    }

    public Builder operationId(String operationId) {
      this.operationId = operationId;
      return this;
    }

    public Endpoint build() {
      Objects.requireNonNull(httpMethod, "httpMethod");
      Objects.requireNonNull(fullPath, "fullPath");
      return new Endpoint(this);
    }
  }
}
