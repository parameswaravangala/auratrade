package com.auratrade.llmgateway;

import java.math.BigDecimal;

/** Spend for one agent on one tier over a period. */
public record SpendRow(String agent, String tier, long runs, long tokens, BigDecimal costUsd) {}
