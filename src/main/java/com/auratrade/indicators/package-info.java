/**
 * Technical indicators as pure functions over BigDecimal series (ADR 0020, moved here by ADR 0038).
 * Depends on nothing else in AuraTrade, so screens, strategies, backtests and paper trading can all
 * use it without cycles.
 */
package com.auratrade.indicators;
