package com.yourco.extractor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class HttpStatusLookup {

  private static final Map<String, Entry> BY_CONSTANT = new LinkedHashMap<>();
  private static final Map<Integer, Entry> BY_CODE = new LinkedHashMap<>();

  private HttpStatusLookup() {}

  public static Optional<Entry> findByConstant(String constant) {
    if (constant == null || constant.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_CONSTANT.get(constant.toUpperCase(Locale.ROOT)));
  }

  public static Optional<Entry> findByCode(int code) {
    return Optional.ofNullable(BY_CODE.get(code));
  }

  public static Optional<String> defaultReason(String code) {
    if (code == null) {
      return Optional.empty();
    }
    try {
      return findByCode(Integer.parseInt(code)).map(Entry::reason);
    } catch (NumberFormatException ex) {
      return Optional.empty();
    }
  }

  private static void register(int code, String reason, String... constantNames) {
    Entry entry = new Entry(code, reason);
    for (String name : constantNames) {
      if (name != null) {
        BY_CONSTANT.put(name.toUpperCase(Locale.ROOT), entry);
      }
    }
    BY_CODE.putIfAbsent(code, entry);
  }

  static {
    register(100, "Continue", "CONTINUE");
    register(101, "Switching Protocols", "SWITCHING_PROTOCOLS");
    register(102, "Processing", "PROCESSING");
    register(103, "Early Hints", "EARLY_HINTS", "CHECKPOINT");
    register(200, "OK", "OK");
    register(201, "Created", "CREATED");
    register(202, "Accepted", "ACCEPTED");
    register(203, "Non-Authoritative Information", "NON_AUTHORITATIVE_INFORMATION");
    register(204, "No Content", "NO_CONTENT");
    register(205, "Reset Content", "RESET_CONTENT");
    register(206, "Partial Content", "PARTIAL_CONTENT");
    register(207, "Multi-Status", "MULTI_STATUS");
    register(208, "Already Reported", "ALREADY_REPORTED");
    register(226, "IM Used", "IM_USED");
    register(300, "Multiple Choices", "MULTIPLE_CHOICES");
    register(301, "Moved Permanently", "MOVED_PERMANENTLY");
    register(302, "Found", "FOUND");
    register(303, "See Other", "SEE_OTHER");
    register(304, "Not Modified", "NOT_MODIFIED");
    register(305, "Use Proxy", "USE_PROXY");
    register(307, "Temporary Redirect", "TEMPORARY_REDIRECT");
    register(308, "Permanent Redirect", "PERMANENT_REDIRECT");
    register(400, "Bad Request", "BAD_REQUEST");
    register(401, "Unauthorized", "UNAUTHORIZED");
    register(402, "Payment Required", "PAYMENT_REQUIRED");
    register(403, "Forbidden", "FORBIDDEN");
    register(404, "Not Found", "NOT_FOUND");
    register(405, "Method Not Allowed", "METHOD_NOT_ALLOWED");
    register(406, "Not Acceptable", "NOT_ACCEPTABLE");
    register(407, "Proxy Authentication Required", "PROXY_AUTHENTICATION_REQUIRED");
    register(408, "Request Timeout", "REQUEST_TIMEOUT");
    register(409, "Conflict", "CONFLICT");
    register(410, "Gone", "GONE");
    register(411, "Length Required", "LENGTH_REQUIRED");
    register(412, "Precondition Failed", "PRECONDITION_FAILED");
    register(413, "Payload Too Large", "PAYLOAD_TOO_LARGE");
    register(414, "URI Too Long", "URI_TOO_LONG");
    register(415, "Unsupported Media Type", "UNSUPPORTED_MEDIA_TYPE");
    register(416, "Requested Range Not Satisfiable", "REQUESTED_RANGE_NOT_SATISFIABLE");
    register(417, "Expectation Failed", "EXPECTATION_FAILED");
    register(418, "I'm a teapot", "I_AM_A_TEAPOT");
    register(421, "Misdirected Request", "MISDIRECTED_REQUEST");
    register(422, "Unprocessable Content", "UNPROCESSABLE_ENTITY");
    register(423, "Locked", "LOCKED");
    register(424, "Failed Dependency", "FAILED_DEPENDENCY");
    register(425, "Too Early", "TOO_EARLY");
    register(426, "Upgrade Required", "UPGRADE_REQUIRED");
    register(428, "Precondition Required", "PRECONDITION_REQUIRED");
    register(429, "Too Many Requests", "TOO_MANY_REQUESTS");
    register(431, "Request Header Fields Too Large", "REQUEST_HEADER_FIELDS_TOO_LARGE");
    register(451, "Unavailable For Legal Reasons", "UNAVAILABLE_FOR_LEGAL_REASONS");
    register(500, "Internal Server Error", "INTERNAL_SERVER_ERROR");
    register(501, "Not Implemented", "NOT_IMPLEMENTED");
    register(502, "Bad Gateway", "BAD_GATEWAY");
    register(503, "Service Unavailable", "SERVICE_UNAVAILABLE");
    register(504, "Gateway Timeout", "GATEWAY_TIMEOUT");
    register(505, "HTTP Version Not Supported", "HTTP_VERSION_NOT_SUPPORTED");
    register(506, "Variant Also Negotiates", "VARIANT_ALSO_NEGOTIATES");
    register(507, "Insufficient Storage", "INSUFFICIENT_STORAGE");
    register(508, "Loop Detected", "LOOP_DETECTED");
    register(510, "Not Extended", "NOT_EXTENDED");
    register(511, "Network Authentication Required", "NETWORK_AUTHENTICATION_REQUIRED");
  }

  public record Entry(int code, String reason) {}
}
