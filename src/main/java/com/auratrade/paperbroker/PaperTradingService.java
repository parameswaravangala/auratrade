package com.auratrade.paperbroker;

import com.auratrade.backtest.CostModel;
import com.auratrade.indicators.Indicators;
import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.marketdata.Interval;
import com.auratrade.marketdata.MarketDataProvider;
import com.auratrade.marketdata.TradingCalendar;
import com.auratrade.paperbroker.PaperRepository.Account;
import com.auratrade.paperbroker.PaperRepository.Order;
import com.auratrade.paperbroker.PaperRepository.Position;
import com.auratrade.screener.Screen;
import com.auratrade.screener.ScreenerService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Paper trading with human approval (ADR 0024, ADR 0005). Code proposes; a person decides; nothing
 * executes without an approval made before the next session's open. Money is BigDecimal.
 *
 * <p>Processing session D for an account, in one transaction: (1) PROPOSED orders for D expire; (2)
 * APPROVED orders for D fill at D's open plus or minus slippage with published costs, sells first,
 * or become UNFILLED with a reason; (3) held positions (except those entered today) count a
 * session; a low at or below the stop, or reaching the holding limit, proposes a SELL for the next
 * session; (4) if the regime allows, the account's screen runs as of D and BUYs are proposed for
 * free slots, sized like the backtest; (5) equity is recorded at D's close.
 *
 * <p>Unlike the backtest there is no intraday stop order: a breach is seen at the close and the
 * exit is proposed for the next open, which is usually a worse price.
 */
public class PaperTradingService {

  private static final Logger log = LoggerFactory.getLogger(PaperTradingService.class);
  private static final int ATR_PERIOD = 14;
  private static final int REGIME_AVERAGE = 200;
  private static final int HISTORY = 260;
  private static final String EXCHANGE = "NSE";

  /** What processing one session did, for logs and the job detail. */
  public record SessionResult(
      long accountId,
      LocalDate session,
      int expired,
      int filled,
      int unfilled,
      int exitsProposed,
      int entriesProposed,
      int reviewsOpened,
      BigDecimal equity,
      String note) {}

  private final PaperRepository repo;
  private final CandleSeriesReader reader;
  private final MarketDataProvider provider;
  private final ScreenerService screener;
  private final TradingCalendar calendar;
  private final TransactionTemplate tx;
  private final JdbcClient jdbc;
  private final Clock clock;
  private final CostModel costs;

  PaperTradingService(
      PaperRepository repo,
      CandleSeriesReader reader,
      MarketDataProvider provider,
      ScreenerService screener,
      TradingCalendar calendar,
      TransactionTemplate tx,
      JdbcClient jdbc,
      Clock clock,
      CostModel costs) {
    this.repo = repo;
    this.reader = reader;
    this.provider = provider;
    this.screener = screener;
    this.calendar = calendar;
    this.tx = tx;
    this.jdbc = jdbc;
    this.clock = clock;
    this.costs = costs;
  }

  // ---- accounts

  /**
   * Opens an account. The go-live criteria must be written now, before the run starts (ADR 0005).
   */
  public Account open(Account a) {
    if (a.goLiveCriteria() == null || a.goLiveCriteria().isBlank()) {
      throw new IllegalArgumentException(
          "write the criteria for ever going live before the paper run starts (ADR 0005)");
    }
    if (screener.definitions().stream().noneMatch(s -> s.name().equals(a.screen()))) {
      throw new IllegalArgumentException("unknown screen '" + a.screen() + "'");
    }
    long id = repo.insertAccount(a, clock.instant());
    return repo.account(id).orElseThrow();
  }

  // ---- decisions

  /**
   * Approves or rejects a PROPOSED order. Allowed only before 09:15 IST on the order's fill date.
   *
   * @throws IllegalStateException if the window has closed or the order is not PROPOSED
   */
  public Order decide(long orderId, boolean approve, String by, String note) {
    return decide(orderId, approve, by, note, null, null);
  }

  /**
   * As {@link #decide(long, boolean, String, String)}, approving with edits recorded next to the
   * proposal.
   *
   * @param quantity a BUY may be approved for 1 up to the proposed quantity, never more, so the
   *     risk sizing is an upper bound; a SELL always closes the whole position; null keeps it
   * @param limitPrice the worst acceptable fill: at most this for a BUY, at least this for a SELL;
   *     if the open with slippage is beyond it, the order is UNFILLED with the reason; null for
   *     none
   */
  public Order decide(
      long orderId, boolean approve, String by, String note, Long quantity, BigDecimal limitPrice) {
    Order o =
        repo.order(orderId).orElseThrow(() -> new IllegalArgumentException("no order " + orderId));
    Instant now = clock.instant();
    if (!now.isBefore(o.decideBy())) {
      throw new IllegalStateException(
          "decision window closed at " + o.fillDate() + " 09:15 IST; the order will expire");
    }
    if (by == null || by.isBlank()) {
      throw new IllegalArgumentException("say who decided");
    }
    if (!approve && (quantity != null || limitPrice != null)) {
      throw new IllegalArgumentException("a rejection takes no quantity or limit price");
    }
    if (quantity != null) {
      if (!o.side().equals("BUY")) {
        throw new IllegalArgumentException("a SELL closes the whole position; quantity is fixed");
      }
      if (quantity < 1 || quantity > o.quantity()) {
        throw new IllegalArgumentException(
            "quantity must be 1 to " + o.quantity() + " (the proposed, risk-sized quantity)");
      }
    }
    if (limitPrice != null && limitPrice.signum() <= 0) {
      throw new IllegalArgumentException("limit price must be positive");
    }
    if (!repo.decide(
        orderId,
        "PROPOSED",
        approve ? "APPROVED" : "REJECTED",
        now,
        by,
        note,
        quantity,
        limitPrice)) {
      throw new IllegalStateException("order " + orderId + " is " + o.status() + ", not PROPOSED");
    }
    return repo.order(orderId).orElseThrow();
  }

  /** The outcome of one order in a bulk decision: the updated order, or why it was refused. */
  public record Outcome(long orderId, boolean ok, Order order, String error) {}

  /** Decides several orders the same way, each on its own: one refusal does not stop the others. */
  public List<Outcome> decideAll(List<Long> orderIds, boolean approve, String by, String note) {
    if (orderIds == null || orderIds.isEmpty()) {
      throw new IllegalArgumentException("no orders given");
    }
    List<Outcome> out = new ArrayList<>();
    for (Long id : new java.util.LinkedHashSet<>(orderIds)) {
      try {
        out.add(new Outcome(id, true, decide(id, approve, by, note), null));
      } catch (IllegalArgumentException | IllegalStateException e) {
        out.add(new Outcome(id, false, null, e.getMessage()));
      }
    }
    return out;
  }

  // ---- reviews (possible corporate actions)

  /**
   * Reconciles a possible corporate action, after a person has checked the exchange's records.
   *
   * @param resolution NO_CORPORATE_ACTION: the gap was a real price move; the position's normal
   *     exit rules resume next session. ADJUSTED: a split or bonus; the position becomes {@code
   *     factor} times the shares at entry and stop divided by {@code factor} (for a 1:1 bonus or a
   *     1:2 split, factor 2), so its value and risk are unchanged
   * @throws IllegalArgumentException for bad input, or a factor giving a fractional quantity
   * @throws IllegalStateException if the review is not open or the position is gone
   */
  public PaperRepository.Review resolveReview(
      long reviewId, String resolution, BigDecimal factor, String by, String note) {
    if (by == null || by.isBlank()) {
      throw new IllegalArgumentException("say who reconciled it");
    }
    return tx.execute(status -> {
      var r =
          repo.review(reviewId)
              .orElseThrow(() -> new IllegalArgumentException("no review " + reviewId));
      if (!r.status().equals("OPEN")) {
        throw new IllegalStateException("review " + reviewId + " is already resolved");
      }
      switch (resolution == null ? "" : resolution) {
        case "NO_CORPORATE_ACTION" -> {
          if (factor != null) {
            throw new IllegalArgumentException("NO_CORPORATE_ACTION takes no factor");
          }
        }
        case "ADJUSTED" -> {
          if (factor == null || factor.signum() <= 0) {
            throw new IllegalArgumentException("ADJUSTED needs a positive factor");
          }
          Position p =
              repo.positions(r.accountId()).stream()
                  .filter(x -> x.instrumentId() == r.instrumentId())
                  .findFirst()
                  .orElseThrow(() ->
                      new IllegalStateException("no position in " + r.symbol() + " to adjust"));
          BigDecimal qty = factor.multiply(BigDecimal.valueOf(p.quantity()));
          if (qty.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                p.quantity()
                    + " shares x "
                    + factor.toPlainString()
                    + " is not a whole number; fractional entitlements are not modelled");
          }
          repo.adjustPosition(
              r.accountId(),
              r.instrumentId(),
              qty.longValueExact(),
              p.entryPrice().divide(factor, 4, RoundingMode.HALF_UP),
              p.stopPrice().divide(factor, 4, RoundingMode.HALF_UP));
        }
        default ->
            throw new IllegalArgumentException(
                "resolution must be NO_CORPORATE_ACTION or ADJUSTED");
      }
      repo.resolveReview(reviewId, resolution, factor, clock.instant(), by, note);
      return repo.review(reviewId).orElseThrow();
    });
  }

  // ---- daily processing

  /** Processes session {@code d} for one account, in one transaction. */
  public SessionResult process(long accountId, LocalDate d) {
    return tx.execute(status -> processInTx(accountId, d));
  }

  private SessionResult processInTx(long accountId, LocalDate d) {
    Account a = repo.account(accountId).orElseThrow();
    String source = provider.name();
    LocalDate next = calendar.nextTradingDay(d);
    BigDecimal slip = BigDecimal.valueOf(a.slippageBps()).movePointLeft(4);
    BigDecimal cash = a.cash();

    // 1. Undecided orders for today expire.
    int expired = 0;
    for (Order o : repo.ordersToFill(accountId, d, "PROPOSED")) {
      if (repo.decide(o.id(), "PROPOSED", "EXPIRED", null, null, "not decided before the open")) {
        expired++;
      }
    }

    // 2. Approved orders for today fill at today's open (sells first).
    List<Order> approved = repo.ordersToFill(accountId, d, "APPROVED");
    Map<Long, Position> held = new HashMap<>();
    repo.positions(accountId).forEach(p -> held.put(p.instrumentId(), p));
    Set<Long> ids = new HashSet<>(held.keySet());
    approved.forEach(o -> ids.add(o.instrumentId()));
    Map<Long, Series> series = load(source, d, ids);
    int filled = 0;
    int unfilled = 0;
    int reviews = 0;
    for (Order o : approved) {
      Candle bar = barOn(series.get(o.instrumentId()), d);
      if (bar == null) {
        repo.markUnfilled(o.id(), "no candle on " + d + " (holiday, suspension or missing data)");
        unfilled++;
        continue;
      }
      // Fail closed: never trade at an open that may be a split or bonus (prices are unadjusted).
      var gap =
          CorporateActionGuard.check(previousClose(series.get(o.instrumentId()), d), bar.open());
      if (gap.isPresent()) {
        repo.markUnfilled(o.id(), "possible corporate action at the open: " + gap.get().describe());
        unfilled++;
        if (held.containsKey(o.instrumentId())
            && openReview(accountId, held.get(o.instrumentId()), d, gap.get())) {
          reviews++;
        }
        continue;
      }
      if (o.side().equals("SELL")) {
        Position p = held.get(o.instrumentId());
        if (p == null) {
          repo.markUnfilled(o.id(), "no position to sell");
          unfilled++;
          continue;
        }
        BigDecimal price =
            bar.open().multiply(BigDecimal.ONE.subtract(slip)).setScale(4, RoundingMode.HALF_UP);
        if (o.limitPrice() != null && price.compareTo(o.limitPrice()) < 0) {
          repo.markUnfilled(o.id(), beyondLimit(price, o));
          unfilled++;
          continue;
        }
        // Trade values are booked in paise, as on a contract note.
        BigDecimal proceeds =
            price.multiply(BigDecimal.valueOf(p.quantity())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cost = costs.sellCost(proceeds);
        cash = cash.add(proceeds).subtract(cost);
        BigDecimal pnl =
            proceeds
                .subtract(
                    p.entryPrice()
                        .multiply(BigDecimal.valueOf(p.quantity()))
                        .setScale(2, RoundingMode.HALF_UP))
                .subtract(cost)
                .subtract(p.entryCosts())
                .setScale(2, RoundingMode.HALF_UP);
        repo.insertTrade(
            accountId,
            p.instrumentId(),
            new PaperRepository.Trade(
                0,
                p.symbol(),
                p.quantity(),
                p.entryDate(),
                p.entryPrice(),
                d,
                price,
                o.reason(),
                p.sessionsHeld(),
                cost.add(p.entryCosts()),
                pnl));
        repo.deletePosition(accountId, p.instrumentId());
        held.remove(p.instrumentId());
        repo.markFilled(o.id(), price, cost);
        filled++;
      } else {
        if (held.containsKey(o.instrumentId())) {
          repo.markUnfilled(o.id(), "already held");
          unfilled++;
          continue;
        }
        BigDecimal price =
            bar.open().multiply(BigDecimal.ONE.add(slip)).setScale(4, RoundingMode.HALF_UP);
        if (o.limitPrice() != null && price.compareTo(o.limitPrice()) > 0) {
          repo.markUnfilled(o.id(), beyondLimit(price, o));
          unfilled++;
          continue;
        }
        long qty = o.quantityToTrade();
        long affordable =
            cash.divide(price.multiply(new BigDecimal("1.01")), 0, RoundingMode.FLOOR).longValue();
        qty = Math.min(qty, affordable);
        if (qty < 1) {
          repo.markUnfilled(o.id(), "not enough cash");
          unfilled++;
          continue;
        }
        BigDecimal value =
            price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cost = costs.buyCost(value);
        cash = cash.subtract(value).subtract(cost);
        Position p =
            new Position(
                accountId, o.instrumentId(), o.symbol(), qty, price, d, cost, o.stopPrice(), 0);
        repo.insertPosition(p);
        held.put(p.instrumentId(), p);
        repo.markFilled(o.id(), price, cost);
        filled++;
      }
    }

    // 3. Exits for positions held before today.
    int exits = 0;
    for (Position p : new ArrayList<>(held.values())) {
      if (!p.entryDate().isBefore(d)) {
        continue;
      }
      Candle bar = barOn(series.get(p.instrumentId()), d);
      if (bar == null) {
        continue;
      }
      // A position under review gets no automatic exit until a person reconciles it.
      if (repo.openReview(accountId, p.instrumentId()).isPresent()) {
        continue;
      }
      var gap =
          CorporateActionGuard.check(previousClose(series.get(p.instrumentId()), d), bar.open());
      if (gap.isPresent()) {
        if (openReview(accountId, p, d, gap.get())) {
          reviews++;
        }
        continue;
      }
      int sessions = p.sessionsHeld() + 1;
      repo.setSessionsHeld(accountId, p.instrumentId(), sessions);
      String reason = null;
      if (bar.low().compareTo(p.stopPrice()) <= 0) {
        reason = "STOP";
      } else if (sessions >= a.maxHoldSessions()) {
        reason = "TIME";
      }
      if (reason != null && !repo.hasOpenOrder(accountId, p.instrumentId(), next)) {
        repo.insertOrder(
            accountId,
            p.instrumentId(),
            p.symbol(),
            "SELL",
            p.quantity(),
            reason,
            d,
            next,
            null,
            null,
            reason.equals("STOP")
                ? "Low "
                    + bar.low().toPlainString()
                    + " reached the stop "
                    + p.stopPrice().toPlainString()
                : "Held " + sessions + " sessions (limit " + a.maxHoldSessions() + ")",
            bar.close(),
            clock.instant());
        exits++;
      }
    }

    // Mark to market at today's close.
    BigDecimal equity = cash;
    for (Position p : held.values()) {
      Candle last = lastOnOrBefore(series.get(p.instrumentId()), d);
      // Under review: keep the pre-gap close, so an unadjusted split books no drawdown.
      var review = repo.openReview(accountId, p.instrumentId());
      BigDecimal mark =
          review.isPresent()
              ? review.get().previousClose()
              : last == null ? p.entryPrice() : last.close();
      equity = equity.add(mark.multiply(BigDecimal.valueOf(p.quantity())));
    }

    // 4. Entry proposals.
    int entries = 0;
    String note = null;
    if (a.regimeFilter() && !regimeAllows(source, d)) {
      note = "regime filter: Nifty 50 not above its 200-session average; no new entries";
    } else {
      int pendingBuys =
          (int)
              repo.orders(accountId, "PROPOSED", 100).stream()
                  .filter(o -> o.side().equals("BUY") && o.fillDate().equals(next))
                  .count();
      int slots = a.maxPositions() - held.size() - pendingBuys;
      if (slots > 0) {
        var result = screener.run(a.screen(), Optional.of(d), ScreenerService.MAX_SHORTLIST);
        Map<String, Long> symbolIds = new HashMap<>();
        for (Screen.Hit h : result.shortlist()) {
          activeShareId(h.symbol()).ifPresent(id -> symbolIds.put(h.symbol(), id));
        }
        Map<Long, Series> candidates = load(source, d, new HashSet<>(symbolIds.values()));
        for (Screen.Hit h : result.shortlist()) {
          if (slots <= 0) {
            break;
          }
          Long id = symbolIds.get(h.symbol());
          if (id == null || held.containsKey(id) || repo.hasOpenOrder(accountId, id, next)) {
            continue;
          }
          Series s = candidates.get(id);
          Candle bar = barOn(s, d);
          Optional<BigDecimal> atr = atr(s);
          if (bar == null || atr.isEmpty()) {
            continue;
          }
          BigDecimal estimate = bar.close();
          BigDecimal stop =
              estimate
                  .subtract(atr.get().multiply(a.atrMultiple()))
                  .setScale(4, RoundingMode.HALF_UP);
          BigDecimal risk = estimate.subtract(stop);
          if (stop.signum() <= 0 || risk.signum() <= 0) {
            continue;
          }
          long byRisk =
              equity.multiply(a.riskPerTrade()).divide(risk, 0, RoundingMode.FLOOR).longValue();
          long byCap =
              equity.multiply(a.maxPosition()).divide(estimate, 0, RoundingMode.FLOOR).longValue();
          long qty = Math.min(byRisk, byCap);
          if (qty < 1) {
            continue;
          }
          repo.insertOrder(
              accountId,
              id,
              h.symbol(),
              "BUY",
              qty,
              "ENTRY",
              d,
              next,
              stop,
              h.score(),
              h.reason()
                  + "; stop "
                  + stop.toPlainString()
                  + " ("
                  + a.atrMultiple().toPlainString()
                  + " x ATR)",
              estimate,
              clock.instant());
          entries++;
          slots--;
        }
      }
    }

    // 5. Record.
    repo.updateCash(accountId, cash.setScale(2, RoundingMode.HALF_UP));
    BigDecimal eq = equity.setScale(2, RoundingMode.HALF_UP);
    repo.upsertEquity(
        accountId,
        new PaperRepository.EquityPoint(
            d, cash.setScale(2, RoundingMode.HALF_UP), eq, held.size()));
    var r =
        new SessionResult(
            accountId, d, expired, filled, unfilled, exits, entries, reviews, eq, note);
    log.info("Paper session {}: {}", d, r);
    return r;
  }

  // ---- helpers

  private static String beyondLimit(BigDecimal price, Order o) {
    return "open with slippage "
        + price.toPlainString()
        + (o.side().equals("BUY") ? " above" : " below")
        + " the limit "
        + o.limitPrice().toPlainString();
  }

  private Map<Long, Series> load(String source, LocalDate d, Set<Long> ids) {
    Map<Long, Series> out = new HashMap<>();
    if (!ids.isEmpty()) {
      reader.forEachInstrument(
          source,
          Interval.DAY,
          d,
          HISTORY,
          new ArrayList<>(ids),
          s -> out.put(s.instrumentId(), s));
    }
    return out;
  }

  /** Close of the bar before {@code d}'s bar, or null without one. */
  private static BigDecimal previousClose(Series s, LocalDate d) {
    if (s == null || s.candles().size() < 2 || barOn(s, d) == null) {
      return null;
    }
    return s.candles().get(s.candles().size() - 2).close();
  }

  /** Opens a review for a held position unless one is open; returns whether it opened one. */
  private boolean openReview(
      long accountId, Position p, LocalDate d, CorporateActionGuard.Evidence e) {
    if (repo.openReview(accountId, p.instrumentId()).isPresent()) {
      return false;
    }
    repo.insertReview(
        accountId,
        p.instrumentId(),
        p.symbol(),
        d,
        e.previousClose(),
        e.open(),
        e.gap(),
        e.nearestRatio(),
        "Possible split or bonus on "
            + d
            + ": "
            + e.describe()
            + ". No automatic exit until reconciled; check the exchange's corporate actions.",
        clock.instant());
    log.warn("Paper account {}: {} {} under review: {}", accountId, p.symbol(), d, e.describe());
    return true;
  }

  private static Candle barOn(Series s, LocalDate d) {
    if (s == null || s.candles().isEmpty()) {
      return null;
    }
    Candle last = s.candles().get(s.candles().size() - 1);
    return last.tradingDate().equals(d) ? last : null;
  }

  private static Candle lastOnOrBefore(Series s, LocalDate d) {
    return s == null || s.candles().isEmpty() ? null : s.candles().get(s.candles().size() - 1);
  }

  private static Optional<BigDecimal> atr(Series s) {
    if (s == null || s.candles().size() < ATR_PERIOD + 1) {
      return Optional.empty();
    }
    List<Candle> c = s.candles().subList(s.candles().size() - ATR_PERIOD - 1, s.candles().size());
    return Indicators.atr(
        c.stream().map(Candle::high).toList(),
        c.stream().map(Candle::low).toList(),
        c.stream().map(Candle::close).toList(),
        ATR_PERIOD);
  }

  /** Nifty 50 above its 200-session average on {@code d}; false without enough history. */
  private boolean regimeAllows(String source, LocalDate d) {
    return reader
        .index(source, Interval.DAY, screener.benchmark(), d, REGIME_AVERAGE)
        .filter(b -> b.candles().size() == REGIME_AVERAGE && d.equals(b.lastDate()))
        .map(b -> {
          List<BigDecimal> closes = b.candles().stream().map(Candle::close).toList();
          return closes
                  .get(closes.size() - 1)
                  .compareTo(Indicators.sma(closes, REGIME_AVERAGE).orElseThrow())
              > 0;
        })
        .orElse(false);
  }

  private Optional<Long> activeShareId(String symbol) {
    return jdbc
        .sql(
            """
            select i.id from instrument i
            where i.exchange = :exchange and i.trading_symbol = :symbol and i.isin is not null
              and i.series in (:series)
              and i.last_seen_on = (select max(last_seen_on) from instrument where exchange = :exchange)
            """)
        .param("exchange", EXCHANGE)
        .param("symbol", symbol)
        .param("series", screener.series())
        .query(Long.class)
        .list()
        .stream()
        .findFirst();
  }

  /** Accounts to process on {@code d}: active, started on or before it, not processed yet. */
  public List<Account> due(LocalDate d) {
    return repo.accounts().stream()
        .filter(Account::active)
        .filter(a -> !a.startedOn().isAfter(d))
        .filter(a -> repo.lastProcessed(a.id()).map(last -> last.isBefore(d)).orElse(true))
        .toList();
  }

  public LocalDate latestCandleDate() {
    return reader
        .latestDate(provider.name(), Interval.DAY)
        .orElseThrow(() -> new IllegalStateException("no candles yet"));
  }
}
