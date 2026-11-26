package com.auratrade.marketdata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Downloads the Upstox NSE instrument file. Checked 2026-11-26: public (no token), gzip, about 2 MB
 * compressed, refreshed daily around 6 AM per the Upstox docs. One request per load, no retries.
 */
final class UpstoxInstrumentFeed implements InstrumentFeed {

  static final String PROVIDER = "UPSTOX";

  private final RestClient http;
  private final URI url;

  UpstoxInstrumentFeed(URI url, Duration timeout) {
    this.url = url;
    var factory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    factory.setReadTimeout(timeout);
    this.http =
        RestClient.builder()
            .requestFactory(factory)
            .defaultHeader("User-Agent", "auratrade")
            .build();
  }

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public List<Instrument> fetch() throws IOException {
    byte[] body = http.get().uri(url).retrieve().body(byte[].class);
    if (body == null || body.length == 0) {
      throw new IOException("empty instrument file from " + url);
    }
    try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
      return UpstoxInstrumentParser.parse(in);
    }
  }
}
