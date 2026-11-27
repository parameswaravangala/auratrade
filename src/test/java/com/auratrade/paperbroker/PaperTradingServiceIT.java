package com.auratrade.paperbroker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.PostgresIntegrationTest;
import com.auratrade.backtest.CostModel;
import com.auratrade.marketdata.CandleSeriesReader;
import com.auratrade.marketdata.MarketDataProvider;
import com.auratrade.marketdata.TradingCalendar;
import com.auratrade.platform.time.MarketZones;
import com.auratrade.screener.ScreenerService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A full paper round trip on made-up data (no exchange data) over the real NSE calendar and the
 * real 52-week breakout screen: propose, approve before the open, fill, stop out, sell, journal.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaperTradingServiceIT extends PostgresIntegrationTest {

  @Autowired PaperRepository repo;
  @Autowired CandleSeriesReader reader;
  @Autowired MarketDataProvider provider;
  @Autowired ScreenerService screener;
  @Autowired TradingCalendar calendar;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcClient jdbc;

  private List<LocalDate> days;
  private LocalDate d; // breakout session
  private LocalDate n1; // entry fill
  private LocalDate n2; // stop breached
  private LocalDate n3; // exit fill

  private PaperTradingService at(Instant now) {
    return new PaperTradingService(
        repo,
        reader,
        provider,
        screener,
        calendar,
        new TransactionTemplate(transactions),
        jdbc,
        Clock.fixed(now, ZoneOffset.UTC),
        CostModel.zerodhaDelivery());
  }

  private static Instant ist(LocalDate date, int hour, int minute) {
    return date.atTime(LocalTime.of(hour, minute)).atZone(MarketZones.IST).toInstant();
  }

  private void candle(long id, LocalDate date, String o, String h, String l, String c, long v) {
    jdbc.sql(
            """
            insert into candle (instrument_id, bar_interval, source, ts, open, high, low, close,
                                volume, ingested_at)
            values (:id, 'DAY', :source, :ts, :o, :h, :l, :c, :v, :now)
            """)
        .param("id", id)
        .param("source", provider.name())
        .param("ts", Timestamp.from(date.atStartOfDay(MarketZones.IST).toInstant()))
        .param("o", new BigDecimal(o))
        .param("h", new BigDecimal(h))
        .param("l", new BigDecimal(l))
        .param("c", new BigDecimal(c))
        .param("v", v)
        .param("now", Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")))
        .update();
  }

  @BeforeEach
  void setUp() {
    jdbc.sql(
            "truncate paper_review, paper_equity, paper_trade, paper_position, paper_order, paper_account,"
                + " candle, backfill_item, backfill_request, instrument_alias,"
                + " instrument_provider_key, instrument restart identity")
        .update();
    long id =
        jdbc.sql(
                """
                insert into instrument (exchange, segment, trading_symbol, series, instrument_type,
                    name, isin, tick_size, lot_size, first_seen_on, last_seen_on)
                values ('NSE', 'EQ', 'ACME', 'EQ', 'EQ', 'ACME LTD', 'INE000A01004', 0.05, 1,
                        '2026-01-01', '2026-01-01')
                returning id
                """)
            .query(Long.class)
            .single();
    days =
        calendar.tradingDaysBetween(LocalDate.parse("2024-01-01"), LocalDate.parse("2025-03-31"));
    // 252 flat sessions (high 101, low 99, close 100, 200,000 shares = INR 2 crore a day).
    int start = days.size() - 257;
    for (int i = start; i < start + 252; i++) {
      candle(id, days.get(i), "100", "101", "99", "100", 200_000);
    }
    d = days.get(start + 252);
    n1 = days.get(start + 253);
    n2 = days.get(start + 254);
    n3 = days.get(start + 255);
    candle(id, d, "104", "106", "103", "105", 600_000); // breakout on 3x volume
    candle(id, n1, "105", "107", "104", "106", 300_000); // entry fills at this open
    candle(id, n2, "104", "104", "90", "92", 400_000); // low 90 breaches the stop
    candle(id, n3, "95", "96", "93", "94", 300_000); // exit fills at this open
  }

  private PaperRepository.Account account(PaperTradingService paper) {
    return paper.open(
        new PaperRepository.Account(
            0,
            "test",
            "52w-high-breakout",
            new BigDecimal("1000000"),
            new BigDecimal("1000000"),
            10,
            new BigDecimal("0.01"),
            new BigDecimal("0.20"),
            BigDecimal.valueOf(3),
            120,
            false,
            5,
            "Paper CAGR above Nifty for 12 months with drawdown under 20%",
            d,
            true));
  }

  @Test
  void aFullRoundTripNeedsAHumanApprovalAtEachStep() {
    var acc = account(at(ist(d, 20, 0)));

    // Session D: the breakout is proposed as a BUY for the next session.
    var r1 = at(ist(d, 20, 15)).process(acc.id(), d);
    assertThat(r1.entriesProposed()).isEqualTo(1);
    var buy = repo.orders(acc.id(), "PROPOSED", 10).get(0);
    assertThat(buy.side()).isEqualTo("BUY");
    assertThat(buy.fillDate()).isEqualTo(n1);
    assertThat(buy.stopPrice()).isLessThan(new BigDecimal("105"));

    // Too late: after 09:15 IST on the fill date the window is closed.
    assertThatThrownBy(() -> at(ist(n1, 9, 15)).decide(buy.id(), true, "param", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("window closed");
    at(ist(n1, 8, 30)).decide(buy.id(), true, "param", "looks fine");

    // Session N1: the approved BUY fills at the open plus 5 bps.
    var r2 = at(ist(n1, 20, 15)).process(acc.id(), n1);
    assertThat(r2.filled()).isEqualTo(1);
    var position = repo.positions(acc.id()).get(0);
    BigDecimal entry =
        new BigDecimal("105").multiply(new BigDecimal("1.0005")).setScale(4, RoundingMode.HALF_UP);
    assertThat(position.entryPrice()).isEqualByComparingTo(entry);
    BigDecimal value =
        entry.multiply(BigDecimal.valueOf(position.quantity())).setScale(2, RoundingMode.HALF_UP);
    BigDecimal cashAfterBuy =
        new BigDecimal("1000000")
            .subtract(value)
            .subtract(CostModel.zerodhaDelivery().buyCost(value));
    assertThat(repo.account(acc.id()).orElseThrow().cash())
        .isEqualByComparingTo(cashAfterBuy.setScale(2, RoundingMode.HALF_UP));

    // Session N2: the low breaches the stop, so a SELL is proposed for N3.
    var r3 = at(ist(n2, 20, 15)).process(acc.id(), n2);
    assertThat(r3.exitsProposed()).isEqualTo(1);
    var sell =
        repo.orders(acc.id(), "PROPOSED", 10).stream()
            .filter(o -> o.side().equals("SELL"))
            .findFirst()
            .orElseThrow();
    assertThat(sell.reason()).isEqualTo("STOP");
    at(ist(n3, 9, 0)).decide(sell.id(), true, "param", null);

    // Session N3: the SELL fills at the open minus 5 bps and the trade is journalled.
    at(ist(n3, 20, 15)).process(acc.id(), n3);
    assertThat(repo.positions(acc.id())).isEmpty();
    var trade = repo.trades(acc.id()).get(0);
    BigDecimal exit =
        new BigDecimal("95").multiply(new BigDecimal("0.9995")).setScale(4, RoundingMode.HALF_UP);
    assertThat(trade.exitPrice()).isEqualByComparingTo(exit);
    assertThat(trade.exitReason()).isEqualTo("STOP");
    BigDecimal proceeds =
        exit.multiply(BigDecimal.valueOf(position.quantity())).setScale(2, RoundingMode.HALF_UP);
    BigDecimal sellCost = CostModel.zerodhaDelivery().sellCost(proceeds);
    BigDecimal expectedPnl =
        proceeds
            .subtract(value)
            .subtract(sellCost)
            .subtract(position.entryCosts())
            .setScale(2, RoundingMode.HALF_UP);
    assertThat(trade.netPnl()).isEqualByComparingTo(expectedPnl);
    assertThat(repo.account(acc.id()).orElseThrow().cash())
        .isEqualByComparingTo(
            cashAfterBuy.add(proceeds).subtract(sellCost).setScale(2, RoundingMode.HALF_UP));
  }

  @Test
  void undecidedProposalsExpireAndNothingIsBought() {
    var acc = account(at(ist(d, 20, 0)));
    at(ist(d, 20, 15)).process(acc.id(), d);

    var r = at(ist(n1, 20, 15)).process(acc.id(), n1);

    assertThat(r.expired()).isEqualTo(1);
    assertThat(r.filled()).isZero();
    assertThat(repo.positions(acc.id())).isEmpty();
    assertThat(repo.orders(acc.id(), "EXPIRED", 10)).hasSize(1);
  }

  private PaperRepository.Order proposedBuy() {
    var acc = account(at(ist(d, 20, 0)));
    at(ist(d, 20, 15)).process(acc.id(), d);
    return repo.orders(acc.id(), "PROPOSED", 10).get(0);
  }

  @Test
  void aProposalCarriesItsReferencePriceAndDeadline() {
    var buy = proposedBuy();

    assertThat(buy.referencePrice()).isNotNull().isPositive();
    assertThat(buy.decideBy()).isEqualTo(ist(n1, 9, 15));
    assertThat(buy.approvedQuantity()).isNull();
  }

  @Test
  void aBuyApprovedForFewerSharesFillsOnlyThose() {
    var buy = proposedBuy();
    long fewer = buy.quantity() / 2;

    var approved = at(ist(n1, 8, 0)).decide(buy.id(), true, "param", "half size", fewer, null);
    at(ist(n1, 20, 15)).process(buy.accountId(), n1);

    assertThat(approved.approvedQuantity()).isEqualTo(fewer);
    assertThat(approved.quantity()).isEqualTo(buy.quantity()); // the proposal is kept
    assertThat(repo.positions(buy.accountId()).get(0).quantity()).isEqualTo(fewer);
  }

  @Test
  void aBuyCannotBeApprovedForMoreThanProposedNorASellResized() {
    var buy = proposedBuy();
    var paper = at(ist(n1, 8, 0));

    assertThatThrownBy(() -> paper.decide(buy.id(), true, "param", null, buy.quantity() + 1, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1 to " + buy.quantity());
    assertThatThrownBy(() -> paper.decide(buy.id(), false, "param", null, 1L, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(repo.order(buy.id()).orElseThrow().status()).isEqualTo("PROPOSED");
  }

  @Test
  void aBuyWhoseOpenIsAboveTheLimitIsUnfilled() {
    var buy = proposedBuy(); // the next open is 105
    at(ist(n1, 8, 0)).decide(buy.id(), true, "param", null, null, new BigDecimal("104.50"));

    var r = at(ist(n1, 20, 15)).process(buy.accountId(), n1);

    assertThat(r.unfilled()).isEqualTo(1);
    assertThat(repo.positions(buy.accountId())).isEmpty();
    var o = repo.order(buy.id()).orElseThrow();
    assertThat(o.status()).isEqualTo("UNFILLED");
    assertThat(o.statusNote()).contains("above the limit 104.5");
  }

  @Test
  void aBuyWithinItsLimitFills() {
    var buy = proposedBuy();
    at(ist(n1, 8, 0)).decide(buy.id(), true, "param", null, null, new BigDecimal("106"));

    assertThat(at(ist(n1, 20, 15)).process(buy.accountId(), n1).filled()).isEqualTo(1);
  }

  @Test
  void aBulkDecisionReportsEachOrderOnItsOwn() {
    var buy = proposedBuy();

    var outcomes = at(ist(n1, 8, 0)).decideAll(List.of(buy.id(), 999_999L), true, "param", "batch");

    assertThat(outcomes).hasSize(2);
    assertThat(outcomes.get(0).ok()).isTrue();
    assertThat(outcomes.get(0).order().status()).isEqualTo("APPROVED");
    assertThat(outcomes.get(1).ok()).isFalse();
    assertThat(outcomes.get(1).error()).contains("no order 999999");
  }

  // ---- corporate-action guard (R0-02)

  private long acmeId() {
    return jdbc.sql("select id from instrument where trading_symbol = 'ACME'")
        .query(Long.class)
        .single();
  }

  private void replace(LocalDate date, String o, String h, String l, String c) {
    jdbc.sql("delete from candle where instrument_id = :id and ts = :ts")
        .param("id", acmeId())
        .param("ts", Timestamp.from(date.atStartOfDay(MarketZones.IST).toInstant()))
        .update();
    candle(acmeId(), date, o, h, l, c, 400_000);
  }

  /** Proposes on D, approves, fills on N1; returns the account id and the position. */
  private PaperRepository.Position held() {
    var buy = proposedBuy();
    at(ist(n1, 8, 0)).decide(buy.id(), true, "param", null);
    at(ist(n1, 20, 15)).process(buy.accountId(), n1);
    return repo.positions(buy.accountId()).get(0);
  }

  @Test
  void aSplitGapOpensAReviewInsteadOfAStopAndKeepsThePreGapMark() {
    var p = held();
    replace(n2, "53", "54", "52", "53"); // 1:2 split: open halves from N1's close of 106

    var r = at(ist(n2, 20, 15)).process(p.accountId(), n2);

    assertThat(r.exitsProposed()).isZero();
    assertThat(r.reviewsOpened()).isEqualTo(1);
    assertThat(repo.orders(p.accountId(), "PROPOSED", 10)).isEmpty();
    var review = repo.reviews(p.accountId(), "OPEN").get(0);
    assertThat(review.previousClose()).isEqualByComparingTo("106");
    assertThat(review.nearestRatio()).isEqualTo("bonus 1:1 or split 1:2");
    // Marked at the pre-gap close: no fake drawdown.
    BigDecimal cash = repo.account(p.accountId()).orElseThrow().cash();
    assertThat(r.equity())
        .isEqualByComparingTo(
            cash.add(new BigDecimal("106").multiply(BigDecimal.valueOf(p.quantity())))
                .setScale(2, RoundingMode.HALF_UP));

    // Next session the low is still under the old stop: still no exit while under review.
    var r3 = at(ist(n3, 20, 15)).process(p.accountId(), n3);
    assertThat(r3.exitsProposed()).isZero();
    assertThat(r3.reviewsOpened()).isZero(); // one open review per position
  }

  @Test
  void reconcilingAsASplitAdjustsThePosition() {
    var p = held();
    replace(n2, "53", "54", "52", "53");
    at(ist(n2, 20, 15)).process(p.accountId(), n2);
    var review = repo.reviews(p.accountId(), "OPEN").get(0);

    var resolved =
        at(ist(n3, 8, 0))
            .resolveReview(review.id(), "ADJUSTED", BigDecimal.valueOf(2), "param", "NSE 1:2");

    assertThat(resolved.status()).isEqualTo("RESOLVED");
    var after = repo.positions(p.accountId()).get(0);
    assertThat(after.quantity()).isEqualTo(p.quantity() * 2);
    assertThat(after.entryPrice())
        .isEqualByComparingTo(
            p.entryPrice().divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
    assertThat(after.stopPrice())
        .isEqualByComparingTo(p.stopPrice().divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
  }

  @Test
  void aFactorGivingFractionalSharesIsRefusedAndNothingChanges() {
    var p = held();
    replace(n2, "53", "54", "52", "53");
    at(ist(n2, 20, 15)).process(p.accountId(), n2);
    var review = repo.reviews(p.accountId(), "OPEN").get(0);
    var factor = p.quantity() % 2 == 0 ? new BigDecimal("1.25") : new BigDecimal("1.5");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> at(ist(n3, 8, 0)).resolveReview(review.id(), "ADJUSTED", factor, "param", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(repo.review(review.id()).orElseThrow().status()).isEqualTo("OPEN");
    assertThat(repo.positions(p.accountId()).get(0).quantity()).isEqualTo(p.quantity());
  }

  @Test
  void reconcilingAsARealMoveLetsTheStopWorkNextSession() {
    var p = held();
    replace(n2, "53", "54", "52", "53");
    at(ist(n2, 20, 15)).process(p.accountId(), n2);
    var review = repo.reviews(p.accountId(), "OPEN").get(0);
    at(ist(n3, 8, 0)).resolveReview(review.id(), "NO_CORPORATE_ACTION", null, "param", "news");
    replace(n3, "52", "53", "50", "51"); // no gap from 53; low still under the stop

    var r = at(ist(n3, 20, 15)).process(p.accountId(), n3);

    assertThat(r.exitsProposed()).isEqualTo(1);
    assertThat(repo.orders(p.accountId(), "PROPOSED", 10).get(0).reason()).isEqualTo("STOP");
  }

  @Test
  void anApprovedSellIsNotFilledAtAGappedOpen() {
    var p = held();
    at(ist(n2, 20, 15)).process(p.accountId(), n2); // low 90 breaches the stop: SELL for N3
    var sell = repo.orders(p.accountId(), "PROPOSED", 10).get(0);
    at(ist(n3, 9, 0)).decide(sell.id(), true, "param", null);
    replace(n3, "46", "47", "45", "46"); // halves from N2's close of 92

    var r = at(ist(n3, 20, 15)).process(p.accountId(), n3);

    assertThat(r.unfilled()).isEqualTo(1);
    assertThat(r.reviewsOpened()).isEqualTo(1);
    assertThat(repo.order(sell.id()).orElseThrow().statusNote())
        .contains("possible corporate action");
    assertThat(repo.positions(p.accountId())).hasSize(1);
    assertThat(repo.trades(p.accountId())).isEmpty();
  }

  @Test
  void aRejectedProposalIsNeverFilled() {
    var acc = account(at(ist(d, 20, 0)));
    at(ist(d, 20, 15)).process(acc.id(), d);
    var buy = repo.orders(acc.id(), "PROPOSED", 10).get(0);
    at(ist(n1, 8, 0)).decide(buy.id(), false, "param", "too extended");

    at(ist(n1, 20, 15)).process(acc.id(), n1);

    assertThat(repo.positions(acc.id())).isEmpty();
    assertThat(repo.order(buy.id()).orElseThrow().status()).isEqualTo("REJECTED");
  }

  @Test
  void anAccountNeedsGoLiveCriteriaWrittenUpFront() {
    assertThatThrownBy(() ->
            at(ist(d, 20, 0))
                .open(
                    new PaperRepository.Account(
                        0,
                        "x",
                        "52w-high-breakout",
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        1,
                        new BigDecimal("0.01"),
                        new BigDecimal("0.2"),
                        BigDecimal.ONE,
                        10,
                        false,
                        5,
                        " ",
                        d,
                        true)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ADR 0005");
  }
}
