package com.auratrade.llmgateway;

import java.math.BigDecimal;
import java.util.List;

/** Month-to-date and today's LLM spend, with the share of the monthly budget used. */
public record SpendReport(
    BigDecimal monthSpendUsd,
    BigDecimal budgetUsd,
    BigDecimal usedPercent,
    List<SpendRow> month,
    List<SpendRow> today) {}
