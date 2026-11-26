package com.auratrade.api;

import com.auratrade.llmgateway.AgentType;
import com.auratrade.llmgateway.BudgetExceededException;
import com.auratrade.llmgateway.LlmGateway;
import com.auratrade.llmgateway.LlmResult;
import com.auratrade.llmgateway.SpendReport;
import com.auratrade.llmgateway.SpendService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dev-only endpoints for poking the gateway by hand. Not registered unless profile dev is on. */
@RestController
@Profile("dev")
@RequestMapping("/dev/llm")
class LlmDevController {

  private final LlmGateway gateway;
  private final SpendService spend;

  LlmDevController(LlmGateway gateway, SpendService spend) {
    this.gateway = gateway;
    this.spend = spend;
  }

  @PostMapping(path = "/ask", consumes = "text/plain")
  LlmResult ask(
      @RequestParam(defaultValue = "ADHOC_CHAT") AgentType agent, @RequestBody String prompt) {
    return gateway.call(agent, "You are a concise assistant.", prompt);
  }

  @GetMapping("/spend")
  SpendReport spend() {
    return spend.report();
  }

  @ExceptionHandler(BudgetExceededException.class)
  ProblemDetail budgetExceeded(BudgetExceededException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
  }
}
