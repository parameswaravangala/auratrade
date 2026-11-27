package com.auratrade.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests that did not come from this machine's browser or tools, until the app has real
 * authentication. Two checks, both fail closed:
 *
 * <ul>
 *   <li>The {@code Host} header must name a loopback host ({@code localhost}, {@code 127.0.0.1} or
 *       {@code [::1]}, any port). This defeats DNS rebinding, where a hostile page makes its own
 *       domain resolve to 127.0.0.1: the browser then sends that domain as the Host.
 *   <li>An {@code Origin} header, when present, must be one of the allowed origins. Browsers send
 *       it on cross-site POSTs, including plain HTML form posts that need no CORS preflight.
 * </ul>
 *
 * <p>The Vite dev proxy keeps the browser's {@code Origin} (http://localhost:5173) and sends Host
 * {@code localhost:5173} or, with changeOrigin, the backend's {@code localhost:<port>}; both pass.
 * Tools such as curl send no Origin and pass on the Host check alone.
 */
public class LocalRequestGuard extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(LocalRequestGuard.class);

  /** Loopback host names, with an optional numeric port. Matched case-insensitively. */
  private static final Pattern LOOPBACK_HOST =
      Pattern.compile("^(localhost|127\\.0\\.0\\.1|\\[::1\\])(:\\d{1,5})?$");

  private final Set<String> allowedOrigins;

  public LocalRequestGuard(Set<String> allowedOrigins) {
    this.allowedOrigins = Set.copyOf(allowedOrigins);
  }

  /** True if the Host header names a loopback address. A missing Host is rejected. */
  static boolean isLoopbackHost(String host) {
    return host != null && LOOPBACK_HOST.matcher(host.trim().toLowerCase(Locale.ROOT)).matches();
  }

  /** True if there is no Origin, or it is exactly one of the allowed origins. */
  boolean isAllowedOrigin(String origin) {
    return origin == null || allowedOrigins.contains(origin);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String host = request.getHeader("Host");
    if (!isLoopbackHost(host)) {
      reject(response, "Requests must be addressed to localhost.", "host", host);
      return;
    }
    String origin = request.getHeader("Origin");
    if (!isAllowedOrigin(origin)) {
      reject(response, "Cross-origin requests are not allowed.", "origin", origin);
      return;
    }
    chain.doFilter(request, response);
  }

  private static void reject(HttpServletResponse response, String detail, String what, String value)
      throws IOException {
    // The rejected value is logged (sanitised), never echoed back to the caller.
    log.warn("Rejected request: {} {}", what, sanitise(value));
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\""
                + detail
                + "\"}");
  }

  private static String sanitise(String value) {
    if (value == null) {
      return "(missing)";
    }
    String cleaned = value.replaceAll("[^\\x20-\\x7E]", "?");
    return cleaned.length() > 100 ? cleaned.substring(0, 100) + "..." : cleaned;
  }
}
