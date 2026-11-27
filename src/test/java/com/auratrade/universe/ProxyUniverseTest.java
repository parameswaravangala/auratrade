package com.auratrade.universe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.marketdata.Candle;
import com.auratrade.universe.ProxyUniverse.Membership;
import com.auratrade.universe.ProxyUniverse.Share;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Calendar: Jan 26 to 30 (January's last session is Jan 30), Feb 2 to 4 (last Feb 4), Mar 2 to 3
 * (last Mar 3). Window 3, size 2. Rebalances: Jan 30 (effective Feb 2) and Feb 4 (effective Mar 2).
 * Mar 3 has no next session, so it is not emitted.
 */
class ProxyUniverseTest {

  private static final List<LocalDate> SESSIONS =
      List.of(
          LocalDate.of(2026, 1, 26),
          LocalDate.of(2026, 1, 27),
          LocalDate.of(2026, 1, 28),
          LocalDate.of(2026, 1, 29),
          LocalDate.of(2026, 1, 30),
          LocalDate.of(2026, 2, 2),
          LocalDate.of(2026, 2, 3),
          LocalDate.of(2026, 2, 4),
          LocalDate.of(2026, 3, 2),
          LocalDate.of(2026, 3, 3));

  private static final LocalDate JAN_30 = LocalDate.of(2026, 1, 30);
  private static final LocalDate FEB_2 = LocalDate.of(2026, 2, 2);
  private static final LocalDate FEB_4 = LocalDate.of(2026, 2, 4);
  private static final LocalDate MAR_2 = LocalDate.of(2026, 3, 2);

  /** A share with the same close on every session and a volume given per session. */
  private static Share share(long id, Function<LocalDate, Long> volumeOn, BigDecimal close) {
    Map<LocalDate, Candle> bars = new HashMap<>();
    for (LocalDate d : SESSIONS) {
      Long v = volumeOn.apply(d);
      if (v != null) {
        bars.put(d, Candle.daily(d, close, close, close, close, v));
      }
    }
    return new Share(id, bars);
  }

  private static Share flat(long id, long volume, String close) {
    return share(id, d -> volume, new BigDecimal(close));
  }

  @Test
  void ranksByMedianTradedValueNotByMean() {
    // Window Jan 28-30. A: volumes 100, 100, 10000 -> median 100 (mean is about 3400).
    // B: volumes 500 on each day -> median 500. B ranks first.
    Share a = share(1, d -> d.equals(JAN_30) ? 10_000L : 100L, BigDecimal.ONE);
    Share b = flat(2, 500, "1");
    List<Membership> m = ProxyUniverse.build(SESSIONS, List.of(a, b), 3, 2);
    assertThat(m.get(0).instrumentIds()).containsExactly(2L, 1L);
  }

  @Test
  void tiesAreBrokenByInstrumentId() {
    List<Membership> m =
        ProxyUniverse.build(SESSIONS, List.of(flat(7, 500, "1"), flat(3, 500, "1")), 3, 2);
    assertThat(m.get(0).instrumentIds()).containsExactly(3L, 7L);
  }

  @Test
  void sizeCapsTheMembershipButCountsEveryEligibleShare() {
    List<Membership> m =
        ProxyUniverse.build(
            SESSIONS, List.of(flat(1, 100, "1"), flat(2, 200, "1"), flat(3, 300, "1")), 3, 2);
    assertThat(m.get(0).instrumentIds()).containsExactly(3L, 2L);
    assertThat(m.get(0).eligible()).isEqualTo(3);
    assertThat(m.get(0).contains(1L)).isFalse();
  }

  @Test
  void aMissingBarInTheWindowExcludesTheShareAndIsCounted() {
    // Share 1 has no bar on Jan 29, which is inside the window for the Jan 30 decision.
    Share gap = share(1, d -> d.equals(LocalDate.of(2026, 1, 29)) ? null : 1_000L, BigDecimal.ONE);
    List<Membership> m = ProxyUniverse.build(SESSIONS, List.of(gap, flat(2, 10, "1")), 3, 2);
    assertThat(m.get(0).instrumentIds()).containsExactly(2L);
    assertThat(m.get(0).excludedShortHistory()).isEqualTo(1);
    assertThat(m.get(0).eligible()).isEqualTo(1);
  }

  @Test
  void zeroTradedValueIsNeverAMember() {
    List<Membership> m =
        ProxyUniverse.build(SESSIONS, List.of(flat(1, 0, "1"), flat(2, 10, "1")), 3, 2);
    assertThat(m.get(0).instrumentIds()).containsExactly(2L);
    assertThat(m.get(0).eligible()).isEqualTo(1);
  }

  @Test
  void rebalancesOnTheLastSessionOfEachMonthAndTakesEffectTheNextSession() {
    List<Membership> m = ProxyUniverse.build(SESSIONS, List.of(flat(1, 10, "1")), 3, 2);
    assertThat(m).extracting(Membership::decidedOn).containsExactly(JAN_30, FEB_4);
    assertThat(m).extracting(Membership::effectiveFrom).containsExactly(FEB_2, MAR_2);
    // Mar 3 is the last session in the calendar and has no next session: not emitted.
  }

  @Test
  void pointInTimeLookupUsesTheMembershipAlreadyInForce() {
    // Share 9 has no bars before Feb 2, so it is ineligible on Jan 30 (window Jan 28-30) and
    // eligible on Feb 4 (window Feb 2-4).
    Share lateOnly = share(9, d -> d.isBefore(FEB_2) ? null : 5_000L, BigDecimal.ONE);
    Share early = flat(4, 10, "1");
    List<Membership> m = ProxyUniverse.build(SESSIONS, List.of(lateOnly, early), 3, 2);
    assertThat(ProxyUniverse.effectiveOn(m, LocalDate.of(2026, 1, 29))).isEmpty();
    assertThat(ProxyUniverse.effectiveOn(m, FEB_2))
        .hasValueSatisfying(x -> assertThat(x.decidedOn()).isEqualTo(JAN_30));
    assertThat(ProxyUniverse.effectiveOn(m, MAR_2))
        .hasValueSatisfying(x -> assertThat(x.decidedOn()).isEqualTo(FEB_4));
    assertThat(ProxyUniverse.effectiveOn(m, FEB_2).orElseThrow().contains(9L)).isFalse();
    assertThat(ProxyUniverse.effectiveOn(m, MAR_2).orElseThrow().contains(9L)).isTrue();
  }

  @Test
  void decisionsNeverReadCandlesAfterTheRebalanceDay() {
    // Same history up to Jan 30; the two runs differ only in the Feb 2 volume, which is after the
    // Jan 30 decision. The Jan 30 membership must be identical.
    Share base1 = flat(1, 100, "1");
    Share base2 = flat(2, 200, "1");
    Share alt1 = share(1, d -> d.equals(FEB_2) ? 9_999_999L : 100L, BigDecimal.ONE);
    List<Membership> a = ProxyUniverse.build(SESSIONS, List.of(base1, base2), 3, 2);
    List<Membership> b = ProxyUniverse.build(SESSIONS, List.of(alt1, base2), 3, 2);
    assertThat(b.get(0)).isEqualTo(a.get(0));
  }

  @Test
  void sessionsMustBeAscendingAndParametersPositive() {
    assertThatThrownBy(
            () -> ProxyUniverse.build(List.of(FEB_2, JAN_30), List.of(flat(1, 1, "1")), 3, 2))
        .hasMessageContaining("ascending");
    assertThatThrownBy(() -> ProxyUniverse.build(SESSIONS, List.of(), 0, 2))
        .hasMessageContaining("positive");
  }

  @Test
  void aMembershipTakesEffectAfterItsDecision() {
    assertThatThrownBy(() -> new Membership(JAN_30, JAN_30, List.of(1L), 1, 0))
        .hasMessageContaining("after decidedOn");
  }
}
