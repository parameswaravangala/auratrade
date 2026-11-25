package com.auratrade.llmgateway;

/** Every kind of LLM-backed agent. Each one must have a policy in configuration. */
public enum AgentType {
  NEWS_TRIAGE,
  STOCK_ANALYSIS,
  RISK_REVIEW,
  DAILY_REPORT,
  ADHOC_CHAT
}
