package com.auratrade.marketdata;

import java.util.List;

/**
 * Outcome of resolving a company name. Callers must handle all three cases; only {@link Resolved}
 * carries a symbol, and that symbol always exists in the instrument table.
 */
public sealed interface Resolution {

  /** How the match was made, strongest first. */
  enum MatchKind {
    ALIAS,
    SYMBOL,
    NAME,
    FUZZY
  }

  record Resolved(ShareRef share, MatchKind kind, double score) implements Resolution {}

  /** Several shares fit about equally well; never pick one. */
  record Ambiguous(List<ShareRef> candidates) implements Resolution {
    public Ambiguous {
      candidates = List.copyOf(candidates);
    }
  }

  record Unresolved(String reason) implements Resolution {}
}
