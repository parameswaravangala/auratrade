package com.auratrade.universe;

import com.auratrade.indicators.Indicators;
import com.auratrade.marketdata.Candle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Point-in-time proxy for the NIFTY 500 universe (ADR 0039). Pure: candles and the session calendar
 * in, memberships out. It ranks every share by the median daily traded value (close x volume) over
 * the last {@code window} sessions and keeps the top {@code size}.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>Rebalance on the last session of each calendar month. The decision uses only the window
 *       ending on that session.
 *   <li>A membership takes effect on the next session. The last rebalance in the calendar has no
 *       next session, so it is not emitted.
 *   <li>A share is eligible only with a candle on every session of the window and a median above
 *       zero. Missing bars make it ineligible, never imputed.
 *   <li>Ties are broken by instrument id, so the output is deterministic.
 * </ul>
 *
 * <p>This is not the official index list: it has no free-float, listing-age or index-rule filters,
 * and it has no membership of shares that have since been delisted (survivorship bias, stated in
 * every research note).
 */
public final class ProxyUniverse {

  public static final int DEFAULT_WINDOW = 126;
  public static final int DEFAULT_SIZE = 500;

  private ProxyUniverse() {}

  /** One share's candles keyed by session date. Only the window before a decision is read. */
  public record Share(long instrumentId, Map<LocalDate, Candle> candlesByDate) {
    public Share {
      Objects.requireNonNull(candlesByDate, "candlesByDate");
      candlesByDate = Map.copyOf(candlesByDate);
    }
  }

  /**
   * The universe in force from {@code effectiveFrom} until the next membership.
   *
   * @param decidedOn last session of the month; the decision used data up to and including it
   * @param effectiveFrom the session after {@code decidedOn}
   * @param instrumentIds members, best median traded value first
   * @param eligible shares that passed every rule at this rebalance
   * @param excludedShortHistory shares with a missing bar in the window
   */
  public record Membership(
      LocalDate decidedOn,
      LocalDate effectiveFrom,
      List<Long> instrumentIds,
      int eligible,
      int excludedShortHistory) {
    public Membership {
      Objects.requireNonNull(decidedOn, "decidedOn");
      Objects.requireNonNull(effectiveFrom, "effectiveFrom");
      instrumentIds = List.copyOf(instrumentIds);
      if (!effectiveFrom.isAfter(decidedOn)) {
        throw new IllegalArgumentException("effectiveFrom must be after decidedOn");
      }
    }

    public boolean contains(long instrumentId) {
      return instrumentIds.contains(instrumentId);
    }
  }

  /**
   * Builds the monthly memberships.
   *
   * @param sessions trading sessions in ascending order (from the trading calendar)
   * @param shares every share to consider
   */
  public static List<Membership> build(
      List<LocalDate> sessions, List<Share> shares, int window, int size) {
    if (window < 1 || size < 1) {
      throw new IllegalArgumentException("window and size must be positive");
    }
    requireAscending(sessions);
    Map<LocalDate, Integer> index = new HashMap<>();
    for (int i = 0; i < sessions.size(); i++) {
      index.put(sessions.get(i), i);
    }
    List<Membership> out = new ArrayList<>();
    for (int i = 0; i < sessions.size() - 1; i++) {
      LocalDate day = sessions.get(i);
      LocalDate next = sessions.get(i + 1);
      if (!YearMonth.from(next).equals(YearMonth.from(day))) {
        // day is the last session of its month and next is the first of the following one.
        if (i + 1 - window < 0) {
          continue;
        }
        out.add(rebalance(sessions, i, next, shares, window, size));
      }
    }
    return out;
  }

  /**
   * The membership in force on {@code session}: the latest one whose effective date is on or before
   * it. Empty before the first membership.
   */
  public static Optional<Membership> effectiveOn(List<Membership> memberships, LocalDate session) {
    Membership found = null;
    for (Membership m : memberships) {
      if (!m.effectiveFrom().isAfter(session)) {
        if (found == null || m.effectiveFrom().isAfter(found.effectiveFrom())) {
          found = m;
        }
      }
    }
    return Optional.ofNullable(found);
  }

  private static Membership rebalance(
      List<LocalDate> sessions,
      int decisionIndex,
      LocalDate effectiveFrom,
      List<Share> shares,
      int window,
      int size) {
    LocalDate decidedOn = sessions.get(decisionIndex);
    List<LocalDate> windowDays = sessions.subList(decisionIndex - window + 1, decisionIndex + 1);

    List<Ranked> ranked = new ArrayList<>();
    int shortHistory = 0;
    for (Share s : shares) {
      Optional<BigDecimal> median = medianTradedValue(s, windowDays);
      if (median.isEmpty()) {
        shortHistory++;
      } else if (median.get().signum() > 0) {
        ranked.add(new Ranked(s.instrumentId(), median.get()));
      }
    }
    ranked.sort(Comparator.comparing(Ranked::median).reversed().thenComparing(Ranked::id));
    List<Long> members = ranked.stream().limit(size).map(Ranked::id).toList();
    return new Membership(decidedOn, effectiveFrom, members, ranked.size(), shortHistory);
  }

  /** Median of close x volume over the window, or empty when any bar in the window is missing. */
  static Optional<BigDecimal> medianTradedValue(Share share, List<LocalDate> windowDays) {
    List<BigDecimal> values = new ArrayList<>(windowDays.size());
    for (LocalDate day : windowDays) {
      Candle c = share.candlesByDate().get(day);
      if (c == null) {
        return Optional.empty();
      }
      values.add(c.close().multiply(BigDecimal.valueOf(c.volume())));
    }
    values.sort(null);
    int n = values.size();
    if (n % 2 == 1) {
      return Optional.of(values.get(n / 2));
    }
    BigDecimal mid = values.get(n / 2 - 1).add(values.get(n / 2));
    return Optional.of(mid.divide(BigDecimal.valueOf(2), Indicators.MC));
  }

  private static void requireAscending(List<LocalDate> sessions) {
    for (int i = 1; i < sessions.size(); i++) {
      if (!sessions.get(i).isAfter(sessions.get(i - 1))) {
        throw new IllegalArgumentException("sessions must be strictly ascending");
      }
    }
  }

  private record Ranked(long id, BigDecimal median) {}
}
