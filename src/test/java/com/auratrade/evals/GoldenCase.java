package com.auratrade.evals;

import java.util.List;
import java.util.Set;

/** One hand-labelled news item. forbiddenSymbols are tickers the model must never output. */
record GoldenCase(
    String id,
    String text,
    boolean relevant,
    String sentiment,
    Set<String> symbols,
    Set<String> forbiddenSymbols,
    List<String> tags) {}
