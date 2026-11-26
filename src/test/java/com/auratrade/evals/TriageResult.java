package com.auratrade.evals;

import java.util.Set;

/** A validated model answer. */
record TriageResult(boolean relevant, String sentiment, Set<String> symbols) {}
