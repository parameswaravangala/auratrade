/**
 * Versioned trading strategies (ADR 0038). A {@link com.auratrade.strategy.Strategy} is code; its
 * parameters are data, validated against the strategy's {@link
 * com.auratrade.strategy.ParameterSchema}; {@link com.auratrade.strategy.StrategyEvaluator} applies
 * the rules every strategy shares (missing data is never a pass, the score is zero unless every
 * required condition passes). Plain Java, no LLM, no I/O.
 */
package com.auratrade.strategy;
