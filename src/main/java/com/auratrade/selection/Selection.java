package com.auratrade.selection;

import com.auratrade.strategy.ConditionResult;
import com.auratrade.strategy.EvaluationStatus;
import com.auratrade.strategy.StrategyEvaluation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Pure ranking for stock selection (plan slice P1). Takes every share's strategy evaluations and
 * returns the shortlist and the "almost qualified" list. No I/O, no database.
 *
 * <p>Shortlist: shares with at least one PASSED strategy, ranked by the best PASSED score, then by
 * symbol. Almost qualified: shares whose best strategy failed exactly one required condition and
 * had nothing unknown (the spec's "almost qualified" list, section 15.4). Everything else is
 * counted, not listed. This is not the combined score: that comes with scoring profiles (P6), and
 * until then the shortlist is ordered by the strongest single strategy.
 */
public final class Selection {

  public static final int MAX_SHORTLIST = 10;

  private Selection() {}

  /** One share and all of its strategy evaluations on the same session. */
  public record ShareEvaluations(
      long instrumentId, String symbol, String name, List<StrategyEvaluation> evaluations) {
    public ShareEvaluations {
      Objects.requireNonNull(symbol, "symbol");
      evaluations = List.copyOf(evaluations);
    }
  }

  /** A share on either list. {@code evaluations} is every strategy's result, passed or not. */
  public record Candidate(
      long instrumentId,
      String symbol,
      String name,
      BigDecimal bestScore,
      List<String> passedStrategies,
      List<StrategyEvaluation> evaluations) {}

  /**
   * @param evaluated shares screened after the guards
   * @param skipped shares not screened, keyed by guard reason
   * @param shortlist top passing shares, at most {@code limit}
   * @param almostQualified shares whose best strategy failed one required condition only
   * @param passing number of shares with at least one PASSED strategy, before the limit
   */
  public record Result(
      int evaluated,
      Map<String, Integer> skipped,
      List<Candidate> shortlist,
      List<Candidate> almostQualified,
      int passing) {
    public Result {
      skipped = Map.copyOf(skipped);
      shortlist = List.copyOf(shortlist);
      almostQualified = List.copyOf(almostQualified);
    }
  }

  public static Result rank(
      List<ShareEvaluations> shares, int limit, Map<String, Integer> skipped) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be at least 1");
    }
    int cap = Math.min(limit, MAX_SHORTLIST);
    List<Candidate> passing = new ArrayList<>();
    List<Candidate> almost = new ArrayList<>();
    for (ShareEvaluations s : shares) {
      List<StrategyEvaluation> passed =
          s.evaluations().stream().filter(e -> e.status() == EvaluationStatus.PASSED).toList();
      if (!passed.isEmpty()) {
        BigDecimal best =
            passed.stream().map(StrategyEvaluation::score).max(BigDecimal::compareTo).orElseThrow();
        passing.add(
            new Candidate(
                s.instrumentId(),
                s.symbol(),
                s.name(),
                best,
                passed.stream().map(StrategyEvaluation::strategyRef).toList(),
                s.evaluations()));
      } else if (isAlmost(s.evaluations())) {
        almost.add(
            new Candidate(
                s.instrumentId(),
                s.symbol(),
                s.name(),
                BigDecimal.ZERO,
                List.of(),
                s.evaluations()));
      }
    }
    Comparator<Candidate> order =
        Comparator.comparing(Candidate::bestScore).reversed().thenComparing(Candidate::symbol);
    passing.sort(order);
    almost.sort(Comparator.comparing(Candidate::symbol));
    return new Result(
        shares.size(),
        new TreeMap<>(skipped),
        passing.stream().limit(cap).toList(),
        almost.stream().limit(cap).toList(),
        passing.size());
  }

  /** True when some strategy failed exactly one required condition and left none unknown. */
  static boolean isAlmost(List<StrategyEvaluation> evaluations) {
    return evaluations.stream().anyMatch(e -> e.status() == EvaluationStatus.PARTIAL);
  }

  /** The conditions that blocked a share, for display: required and not passed. */
  public static List<ConditionResult> blockers(StrategyEvaluation e) {
    return e.blockers();
  }
}
