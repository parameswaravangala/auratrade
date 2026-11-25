package com.auratrade;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuraTradeApplicationIT extends PostgresIntegrationTest {

  @Value("${local.server.port}")
  int port;

  @Autowired JdbcClient jdbc;

  @Test
  void actuatorHealthIsUp() throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).build();

    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }

  @Test
  void migrationsCreateVectorExtensionAndUsageTable() {
    Long extension =
        jdbc.sql("select count(*) from pg_extension where extname = 'vector'")
            .query(Long.class)
            .single();
    Long table =
        jdbc.sql("select count(*) from information_schema.tables where table_name = 'llm_usage'")
            .query(Long.class)
            .single();

    assertThat(extension).isEqualTo(1L);
    assertThat(table).isEqualTo(1L);
  }
}
