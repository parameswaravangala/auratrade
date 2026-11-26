package com.auratrade.marketdata;

/** A market data provider call failed. Subtypes say why, so callers and alerts can react. */
public class ProviderException extends RuntimeException {

  private final String provider;

  public ProviderException(String provider, String message) {
    super(provider + ": " + message);
    this.provider = provider;
  }

  public ProviderException(String provider, String message, Throwable cause) {
    super(provider + ": " + message, cause);
    this.provider = provider;
  }

  public String provider() {
    return provider;
  }

  /** Missing, expired or revoked credentials. Needs a human (new token or login). */
  public static class Auth extends ProviderException {
    public Auth(String provider, String message) {
      super(provider, message);
    }
  }

  /** The provider throttled us despite the client-side limiter. */
  public static class RateLimited extends ProviderException {
    private final java.time.Duration retryAfter;

    public RateLimited(String provider, String message) {
      this(provider, message, null);
    }

    /**
     * @param retryAfter how long the provider asked us to wait (a Retry-After header), or null
     */
    public RateLimited(String provider, String message, java.time.Duration retryAfter) {
      super(provider, message);
      this.retryAfter = retryAfter;
    }

    public java.util.Optional<java.time.Duration> retryAfter() {
      return java.util.Optional.ofNullable(retryAfter);
    }
  }

  /** The provider answered with an error or a response we do not understand. Nothing is stored. */
  public static class BadResponse extends ProviderException {
    public BadResponse(String provider, String message) {
      super(provider, message);
    }

    public BadResponse(String provider, String message, Throwable cause) {
      super(provider, message, cause);
    }
  }
}
