package com.auratrade.llmgateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Talks to a real local Ollama. Opt in with OLLAMA_IT=true, with Ollama running and the model from
 * OLLAMA_MODEL (default llama3.1:8b) already pulled.
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_IT", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OllamaSmokeIT extends PostgresIntegrationTest {

  @Autowired LlmGateway gateway;
  @Autowired JdbcClient jdbc;

  @Test
  void answersAndRecordsUsage() {
    jdbc.sql("truncate llm_usage").update();

    LlmResult result =
        gateway.call(AgentType.ADHOC_CHAT, "Answer in one short sentence.", "What is 2 + 2?");

    assertThat(result.text()).isNotBlank();
    assertThat(result.usage().total()).isPositive();
    Long rows = jdbc.sql("select count(*) from llm_usage").query(Long.class).single();
    assertThat(rows).isEqualTo(1L);
  }
}
