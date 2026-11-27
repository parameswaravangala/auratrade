package com.auratrade.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auratrade.llmgateway.AgentType;
import com.auratrade.llmgateway.LlmGateway;
import com.auratrade.llmgateway.SpendService;
import com.auratrade.platform.web.LocalRequestGuard;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The Host/Origin guard and the JSON-only ask endpoint, through MockMvc. No Spring context: the
 * guard and controller are wired by hand, so the gateway is never really called.
 */
class LocalRequestGuardMvcTest {

  private static final String ASK = "/dev/llm/ask";
  private static final String JSON_BODY = "{\"prompt\":\"hello\"}";

  private LlmGateway gateway;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    gateway = mock(LlmGateway.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new LlmDevController(gateway, mock(SpendService.class)))
            .addFilters(
                new LocalRequestGuard(Set.of("http://localhost:5173", "http://localhost:8080")))
            .build();
  }

  @Test
  void allowsLoopbackHostWithoutOrigin() throws Exception {
    mvc.perform(
            post(ASK)
                .header("Host", "localhost:8080")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_BODY))
        .andExpect(status().isOk());
    verify(gateway).call(eq(AgentType.ADHOC_CHAT), any(), eq("hello"));
  }

  @Test
  void allowsViteDevProxy() throws Exception {
    // Vite forwards the browser's Origin; Host is the Vite port, or the backend's with
    // changeOrigin (8080, or another port such as 8081 via AURATRADE_API).
    for (String host : new String[] {"localhost:5173", "localhost:8080", "localhost:8081"}) {
      mvc.perform(
              post(ASK)
                  .header("Host", host)
                  .header("Origin", "http://localhost:5173")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(JSON_BODY))
          .andExpect(status().isOk());
    }
  }

  @Test
  void allowsIpLoopbackHosts() throws Exception {
    for (String host : new String[] {"127.0.0.1", "127.0.0.1:8080", "[::1]", "[::1]:8080"}) {
      mvc.perform(
              post(ASK)
                  .header("Host", host)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(JSON_BODY))
          .andExpect(status().isOk());
    }
  }

  @Test
  void rejectsForeignHost() throws Exception {
    // A LAN address, or a rebinding domain that resolves to 127.0.0.1.
    for (String host :
        new String[] {
          "192.168.1.20:8080", "evil.example", "localhost.evil.example", "localhost:x"
        }) {
      mvc.perform(
              post(ASK)
                  .header("Host", host)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(JSON_BODY))
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
          .andExpect(jsonPath("$.status").value(403));
    }
    verify(gateway, never()).call(any(), any(), any());
  }

  @Test
  void rejectsMissingHost() throws Exception {
    mvc.perform(post(ASK).contentType(MediaType.APPLICATION_JSON).content(JSON_BODY))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsForeignOrigin() throws Exception {
    for (String origin :
        new String[] {
          "https://evil.example", "null", "http://localhost:3000", "http://127.0.0.1"
        }) {
      mvc.perform(
              post(ASK)
                  .header("Host", "localhost:8080")
                  .header("Origin", origin)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(JSON_BODY))
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
    verify(gateway, never()).call(any(), any(), any());
  }

  @Test
  void rejectsTextPlainAsk() throws Exception {
    // text/plain is what a cross-site HTML form can send without a CORS preflight.
    mvc.perform(
            post(ASK)
                .header("Host", "localhost:8080")
                .contentType(MediaType.TEXT_PLAIN)
                .content("hello"))
        .andExpect(status().isUnsupportedMediaType());
    verify(gateway, never()).call(any(), any(), any());
  }

  @Test
  void rejectsBlankPrompt() throws Exception {
    mvc.perform(
            post(ASK)
                .header("Host", "localhost:8080")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\" \"}"))
        .andExpect(status().isBadRequest());
  }
}
