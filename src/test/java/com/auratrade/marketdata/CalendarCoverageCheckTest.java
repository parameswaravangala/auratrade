package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CalendarCoverageCheckTest {

  private static TradingCalendar covering(Integer... years) {
    return new TradingCalendar(Set.of(), Set.of(), Set.of(years));
  }

  @Test
  void quietWhenTheCurrentYearIsCoveredBeforeDecember() {
    assertThat(CalendarCoverageCheck.alerts(covering(2026), LocalDate.parse("2026-11-30")))
        .isEmpty();
  }

  @Test
  void warnsInDecemberWhenNextYearIsMissing() {
    var alerts = CalendarCoverageCheck.alerts(covering(2026), LocalDate.parse("2026-12-01"));

    assertThat(alerts).singleElement().asString().contains("2027", "fail closed");
  }

  @Test
  void quietInDecemberWhenNextYearIsCovered() {
    assertThat(CalendarCoverageCheck.alerts(covering(2026, 2027), LocalDate.parse("2026-12-31")))
        .isEmpty();
  }

  @Test
  void warnsWhenTheCurrentYearIsMissing() {
    var alerts = CalendarCoverageCheck.alerts(covering(), LocalDate.parse("2026-06-15"));

    assertThat(alerts).singleElement().asString().contains("2026");
  }

  @Test
  void warnsAboutBothYearsInDecemberOfAnUncoveredYear() {
    assertThat(CalendarCoverageCheck.alerts(covering(), LocalDate.parse("2026-12-10"))).hasSize(2);
  }
}
