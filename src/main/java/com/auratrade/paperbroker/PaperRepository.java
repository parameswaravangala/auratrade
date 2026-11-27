package com.auratrade.paperbroker;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Paper trading tables (V9). Plain JDBC. */
public class PaperRepository {

  public record Account(
      long id,
      String name,
      String screen,
      BigDecimal initialCapital,
      BigDecimal cash,
      int maxPositions,
      BigDecimal riskPerTrade,
      BigDecimal maxPosition,
      BigDecimal atrMultiple,
      int maxHoldSessions,
      boolean regimeFilter,
      int slippageBps,
      String goLiveCriteria,
      LocalDate startedOn,
      boolean active) {}

  public record Order(
      long id,
      long accountId,
      long instrumentId,
      String symbol,
      String side,
      long quantity,
      String reason,
      LocalDate signalDate,
      LocalDate fillDate,
      BigDecimal stopPrice,
      BigDecimal score,
      String detail,
      String status,
      Instant decidedAt,
      String decidedBy,
      BigDecimal fillPrice,
      BigDecimal fillCosts,
      String statusNote,
      BigDecimal referencePrice,
      Long approvedQuantity,
      BigDecimal limitPrice,
      Instant decideBy) {

    /** Shares to trade if approved: the approved quantity, or the proposed one. */
    public long quantityToTrade() {
      return approvedQuantity == null ? quantity : approvedQuantity;
    }
  }

  public record Position(
      long accountId,
      long instrumentId,
      String symbol,
      long quantity,
      BigDecimal entryPrice,
      LocalDate entryDate,
      BigDecimal entryCosts,
      BigDecimal stopPrice,
      int sessionsHeld) {}

  public record Trade(
      long id,
      String symbol,
      long quantity,
      LocalDate entryDate,
      BigDecimal entryPrice,
      LocalDate exitDate,
      BigDecimal exitPrice,
      String exitReason,
      int sessionsHeld,
      BigDecimal costs,
      BigDecimal netPnl) {}

  public record EquityPoint(LocalDate date, BigDecimal cash, BigDecimal equity, int positions) {}

  private final JdbcClient jdbc;

  public PaperRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  // ---- accounts

  long insertAccount(Account a, Instant now) {
    return jdbc.sql(
            """
            insert into paper_account (name, screen, initial_capital, cash, max_positions,
                risk_per_trade, max_position, atr_multiple, max_hold_sessions, regime_filter,
                slippage_bps, go_live_criteria, started_on, active, created_at)
            values (:name, :screen, :capital, :capital, :maxPositions, :risk, :maxPosition, :atr,
                :hold, :regime, :slip, :criteria, :started, true, :now)
            returning id
            """)
        .param("name", a.name())
        .param("screen", a.screen())
        .param("capital", a.initialCapital())
        .param("maxPositions", a.maxPositions())
        .param("risk", a.riskPerTrade())
        .param("maxPosition", a.maxPosition())
        .param("atr", a.atrMultiple())
        .param("hold", a.maxHoldSessions())
        .param("regime", a.regimeFilter())
        .param("slip", a.slippageBps())
        .param("criteria", a.goLiveCriteria())
        .param("started", a.startedOn())
        .param("now", Timestamp.from(now))
        .query(Long.class)
        .single();
  }

  private static final String ACCOUNT_COLUMNS =
      "id, name, screen, initial_capital, cash, max_positions, risk_per_trade, max_position,"
          + " atr_multiple, max_hold_sessions, regime_filter, slippage_bps, go_live_criteria,"
          + " started_on, active";

  private static Account account(ResultSet rs, int n) throws SQLException {
    return new Account(
        rs.getLong(1),
        rs.getString(2),
        rs.getString(3),
        rs.getBigDecimal(4),
        rs.getBigDecimal(5),
        rs.getInt(6),
        rs.getBigDecimal(7),
        rs.getBigDecimal(8),
        rs.getBigDecimal(9),
        rs.getInt(10),
        rs.getBoolean(11),
        rs.getInt(12),
        rs.getString(13),
        rs.getObject(14, LocalDate.class),
        rs.getBoolean(15));
  }

  public Optional<Account> account(long id) {
    return jdbc.sql("select " + ACCOUNT_COLUMNS + " from paper_account where id = :id")
        .param("id", id)
        .query(PaperRepository::account)
        .optional();
  }

  public List<Account> accounts() {
    return jdbc.sql("select " + ACCOUNT_COLUMNS + " from paper_account order by id")
        .query(PaperRepository::account)
        .list();
  }

  void updateCash(long accountId, BigDecimal cash) {
    jdbc.sql("update paper_account set cash = :cash where id = :id")
        .param("cash", cash)
        .param("id", accountId)
        .update();
  }

  // ---- orders

  long insertOrder(
      long accountId,
      long instrumentId,
      String symbol,
      String side,
      long quantity,
      String reason,
      LocalDate signalDate,
      LocalDate fillDate,
      BigDecimal stop,
      BigDecimal score,
      String detail,
      BigDecimal referencePrice,
      Instant now) {
    return jdbc.sql(
            """
            insert into paper_order (account_id, instrument_id, symbol, side, quantity, reason,
                signal_date, fill_date, stop_price, score, detail, status, created_at,
                reference_price)
            values (:account, :instrument, :symbol, :side, :qty, :reason, :signal, :fill, :stop,
                :score, :detail, 'PROPOSED', :now, :reference)
            returning id
            """)
        .param("account", accountId)
        .param("instrument", instrumentId)
        .param("symbol", symbol)
        .param("side", side)
        .param("qty", quantity)
        .param("reason", reason)
        .param("signal", signalDate)
        .param("fill", fillDate)
        .param("stop", stop)
        .param("score", score)
        .param("detail", detail)
        .param("reference", referencePrice)
        .param("now", Timestamp.from(now))
        .query(Long.class)
        .single();
  }

  private static final String ORDER_COLUMNS =
      "id, account_id, instrument_id, symbol, side, quantity, reason, signal_date, fill_date,"
          + " stop_price, score, detail, status, decided_at, decided_by, fill_price, fill_costs,"
          + " status_note, reference_price, approved_quantity, limit_price";

  private static Order order(ResultSet rs, int n) throws SQLException {
    Timestamp decided = rs.getTimestamp(14);
    return new Order(
        rs.getLong(1),
        rs.getLong(2),
        rs.getLong(3),
        rs.getString(4),
        rs.getString(5),
        rs.getLong(6),
        rs.getString(7),
        rs.getObject(8, LocalDate.class),
        rs.getObject(9, LocalDate.class),
        rs.getBigDecimal(10),
        rs.getBigDecimal(11),
        rs.getString(12),
        rs.getString(13),
        decided == null ? null : decided.toInstant(),
        rs.getString(15),
        rs.getBigDecimal(16),
        rs.getBigDecimal(17),
        rs.getString(18),
        rs.getBigDecimal(19),
        rs.getObject(20, Long.class),
        rs.getBigDecimal(21),
        decideBy(rs.getObject(9, LocalDate.class)));
  }

  /** Decisions are accepted until 09:15 IST on the fill date (ADR 0024). */
  static final java.time.LocalTime DECISION_DEADLINE = java.time.LocalTime.of(9, 15);

  static Instant decideBy(LocalDate fillDate) {
    return fillDate
        .atTime(DECISION_DEADLINE)
        .atZone(com.auratrade.platform.time.MarketZones.IST)
        .toInstant();
  }

  public Optional<Order> order(long id) {
    return jdbc.sql("select " + ORDER_COLUMNS + " from paper_order where id = :id")
        .param("id", id)
        .query(PaperRepository::order)
        .optional();
  }

  /** Orders of an account, newest first, optionally filtered by status. */
  public List<Order> orders(long accountId, String status, int limit) {
    return jdbc.sql(
            "select "
                + ORDER_COLUMNS
                + " from paper_order where account_id = :account"
                + " and (cast(:status as text) is null or status = cast(:status as text))"
                + " order by id desc limit :limit")
        .param("account", accountId)
        .param("status", status)
        .param("limit", limit)
        .query(PaperRepository::order)
        .list();
  }

  List<Order> ordersToFill(long accountId, LocalDate fillDate, String status) {
    return jdbc.sql(
            "select "
                + ORDER_COLUMNS
                + " from paper_order where account_id = :account and fill_date = :fill"
                + " and status = :status order by side desc, id")
        .param("account", accountId)
        .param("fill", fillDate)
        .param("status", status)
        .query(PaperRepository::order)
        .list();
  }

  /** Changes status only if the order is still in {@code expected}; returns whether it did. */
  boolean decide(long id, String expected, String status, Instant at, String by, String note) {
    return decide(id, expected, status, at, by, note, null, null);
  }

  /** As above, also recording an approved quantity and a limit price (either may be null). */
  boolean decide(
      long id,
      String expected,
      String status,
      Instant at,
      String by,
      String note,
      Long approvedQuantity,
      BigDecimal limitPrice) {
    return jdbc.sql(
                """
                update paper_order set status = :status, decided_at = :at, decided_by = :by,
                    status_note = :note, approved_quantity = :qty, limit_price = :limit
                where id = :id and status = :expected
                """)
            .param("qty", approvedQuantity)
            .param("limit", limitPrice)
            .param("status", status)
            .param("at", at == null ? null : Timestamp.from(at))
            .param("by", by)
            .param("note", note)
            .param("id", id)
            .param("expected", expected)
            .update()
        == 1;
  }

  void markFilled(long id, BigDecimal price, BigDecimal costs) {
    jdbc.sql(
            "update paper_order set status = 'FILLED', fill_price = :price, fill_costs = :costs"
                + " where id = :id")
        .param("price", price)
        .param("costs", costs)
        .param("id", id)
        .update();
  }

  void markUnfilled(long id, String note) {
    jdbc.sql("update paper_order set status = 'UNFILLED', status_note = :note where id = :id")
        .param("note", note)
        .param("id", id)
        .update();
  }

  boolean hasOpenOrder(long accountId, long instrumentId, LocalDate fillDate) {
    return jdbc.sql(
                """
                select count(*) from paper_order
                where account_id = :account and instrument_id = :instrument and fill_date = :fill
                  and status in ('PROPOSED', 'APPROVED')
                """)
            .param("account", accountId)
            .param("instrument", instrumentId)
            .param("fill", fillDate)
            .query(Long.class)
            .single()
        > 0;
  }

  // ---- positions, trades, equity

  public List<Position> positions(long accountId) {
    return jdbc.sql(
            """
            select account_id, instrument_id, symbol, quantity, entry_price, entry_date,
                   entry_costs, stop_price, sessions_held
            from paper_position where account_id = :account order by symbol
            """)
        .param("account", accountId)
        .query((rs, n) ->
            new Position(
                rs.getLong(1),
                rs.getLong(2),
                rs.getString(3),
                rs.getLong(4),
                rs.getBigDecimal(5),
                rs.getObject(6, LocalDate.class),
                rs.getBigDecimal(7),
                rs.getBigDecimal(8),
                rs.getInt(9)))
        .list();
  }

  void insertPosition(Position p) {
    jdbc.sql(
            """
            insert into paper_position (account_id, instrument_id, symbol, quantity, entry_price,
                entry_date, entry_costs, stop_price, sessions_held)
            values (:account, :instrument, :symbol, :qty, :price, :date, :costs, :stop, 0)
            """)
        .param("account", p.accountId())
        .param("instrument", p.instrumentId())
        .param("symbol", p.symbol())
        .param("qty", p.quantity())
        .param("price", p.entryPrice())
        .param("date", p.entryDate())
        .param("costs", p.entryCosts())
        .param("stop", p.stopPrice())
        .update();
  }

  void setSessionsHeld(long accountId, long instrumentId, int sessions) {
    jdbc.sql(
            "update paper_position set sessions_held = :s where account_id = :a and instrument_id = :i")
        .param("s", sessions)
        .param("a", accountId)
        .param("i", instrumentId)
        .update();
  }

  void deletePosition(long accountId, long instrumentId) {
    jdbc.sql("delete from paper_position where account_id = :a and instrument_id = :i")
        .param("a", accountId)
        .param("i", instrumentId)
        .update();
  }

  void insertTrade(long accountId, long instrumentId, Trade t) {
    jdbc.sql(
            """
            insert into paper_trade (account_id, instrument_id, symbol, quantity, entry_date,
                entry_price, exit_date, exit_price, exit_reason, sessions_held, costs, net_pnl)
            values (:account, :instrument, :symbol, :qty, :entryDate, :entryPrice, :exitDate,
                :exitPrice, :reason, :sessions, :costs, :pnl)
            """)
        .param("account", accountId)
        .param("instrument", instrumentId)
        .param("symbol", t.symbol())
        .param("qty", t.quantity())
        .param("entryDate", t.entryDate())
        .param("entryPrice", t.entryPrice())
        .param("exitDate", t.exitDate())
        .param("exitPrice", t.exitPrice())
        .param("reason", t.exitReason())
        .param("sessions", t.sessionsHeld())
        .param("costs", t.costs())
        .param("pnl", t.netPnl())
        .update();
  }

  public List<Trade> trades(long accountId) {
    return jdbc.sql(
            """
            select id, symbol, quantity, entry_date, entry_price, exit_date, exit_price,
                   exit_reason, sessions_held, costs, net_pnl
            from paper_trade where account_id = :account order by exit_date desc, id desc
            """)
        .param("account", accountId)
        .query((rs, n) ->
            new Trade(
                rs.getLong(1),
                rs.getString(2),
                rs.getLong(3),
                rs.getObject(4, LocalDate.class),
                rs.getBigDecimal(5),
                rs.getObject(6, LocalDate.class),
                rs.getBigDecimal(7),
                rs.getString(8),
                rs.getInt(9),
                rs.getBigDecimal(10),
                rs.getBigDecimal(11)))
        .list();
  }

  void upsertEquity(long accountId, EquityPoint p) {
    jdbc.sql(
            """
            insert into paper_equity (account_id, session_date, cash, equity, positions)
            values (:account, :date, :cash, :equity, :positions)
            on conflict (account_id, session_date) do update set
              cash = excluded.cash, equity = excluded.equity, positions = excluded.positions
            """)
        .param("account", accountId)
        .param("date", p.date())
        .param("cash", p.cash())
        .param("equity", p.equity())
        .param("positions", p.positions())
        .update();
  }

  public List<EquityPoint> equity(long accountId) {
    return jdbc.sql(
            """
            select session_date, cash, equity, positions from paper_equity
            where account_id = :account order by session_date
            """)
        .param("account", accountId)
        .query((rs, n) ->
            new EquityPoint(
                rs.getObject(1, LocalDate.class),
                rs.getBigDecimal(2),
                rs.getBigDecimal(3),
                rs.getInt(4)))
        .list();
  }

  Optional<LocalDate> lastProcessed(long accountId) {
    return jdbc.sql("select max(session_date) from paper_equity where account_id = :account")
        .param("account", accountId)
        .query(LocalDate.class)
        .optional();
  }
}
