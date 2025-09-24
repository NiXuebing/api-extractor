package com.yourco.extractor.model;

import java.util.Objects;

public class EndpointResponse {

  private final String statusCode;
  private final Payload payload;
  private final String description;

  private EndpointResponse(Builder builder) {
    this.statusCode = builder.statusCode;
    this.payload = builder.payload;
    this.description = builder.description;
  }

  public String getStatusCode() {
    return statusCode;
  }

  public Payload getPayload() {
    return payload;
  }

  public String getDescription() {
    return description;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String statusCode;
    private Payload payload;
    private String description;

    private Builder() {}

    public Builder statusCode(String statusCode) {
      this.statusCode = statusCode;
      return this;
    }

    public Builder payload(Payload payload) {
      this.payload = payload;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public EndpointResponse build() {
      Objects.requireNonNull(statusCode, "statusCode");
      return new EndpointResponse(this);
    }
  }
}
