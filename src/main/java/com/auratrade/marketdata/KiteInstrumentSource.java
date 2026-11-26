package com.auratrade.marketdata;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Downloads the Kite instruments CSV. Checked 2026-11-26: the NSE file downloads without
 * authentication (about 1 MB, 10,500 rows). One request per load; no retries, so a failed download
 * simply fails the load.
 */
final class KiteInstrumentSource implements InstrumentSource {

  private final RestClient http;
  private final URI url;

  KiteInstrumentSource(URI url, Duration timeout) {
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
  public Reader open() throws IOException {
    String body = http.get().uri(url).retrieve().body(String.class);
    if (body == null || body.isBlank()) {
      throw new IOException("empty instrument file from " + url);
    }
    return new StringReader(body);
  }
}
