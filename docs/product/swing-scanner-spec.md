# Claude Instructions: AuraTrade NIFTY 500 Multi-Strategy Swing-Trading Scanner

## 1. Role and objective

Act as the quantitative research and implementation assistant for
**AuraTrade**, an Indian-equity swing-trading research platform.

Design and implement a systematic stock-selection engine for the **NIFTY
500 universe**. The engine should evaluate five complementary
swing-trading strategies, rank qualifying stocks, and produce a daily
shortlist of the **top 10 candidates** with an explainable trade plan.

The initial operating mode must be **research and paper trading only**.
Do not place live orders or imply that any signal guarantees a profit. A
strategy can lose money, and past or backtested performance does not
guarantee future results.

Prioritize: - Reproducible, rule-based screening rather than subjective
stock tips. - Explainable signals and auditable calculations. -
Reliable, legally usable market and fundamental data. - Capital
preservation and realistic execution assumptions. - Backtesting,
out-of-sample validation, and paper-trading evidence before live use.

## 2. Stock universe and data requirements

### Universe

-   Use the current NIFTY 500 constituents.
-   Maintain constituent history where possible to avoid survivorship
    bias in historical backtests.
-   Exclude suspended, untradeable, or insufficiently liquid securities.
-   Apply configurable liquidity filters. Document the chosen thresholds
    and why they are appropriate.
-   Treat corporate actions, symbol changes, splits, bonuses, dividends,
    and delistings correctly.

### Required market data

At minimum, obtain adjusted daily OHLCV data: - Open, high, low, close,
volume. - Corporate-action-adjusted prices or a reliable adjustment
process. - At least 3--5 years of daily history where available for
research and backtesting. - Benchmark data for NIFTY 50 and relevant
sector indices. - Sector and industry classification. - Delivery-volume
data only where a reliable, permitted source is available.

OHLCV means **Open, High, Low, Close, and Volume**.

### Fundamental and event data

Where reliable and legally accessible, collect: - Quarterly revenue,
profit, EPS, and operating margins. - Earnings announcement dates and
results. - Earnings surprises, where consistently available. - Corporate
announcements and material company news. - Relevant regulatory, policy,
and sector news. - Financial-statement red flags and major deterioration
indicators.

Do not fabricate missing data. Mark unavailable fields as unknown and
reduce confidence or exclude the affected rule from scoring. Record
source, timestamp, update frequency, licensing constraints, and data
quality for every feed. Do not scrape or redistribute exchange data in
violation of exchange terms or vendor licences.

## 3. The five strategies

Implement each strategy as a separately testable module. A stock may
qualify for more than one strategy.

### Strategy 1 --- Momentum breakout (highest priority)

**Concept:** Buy stocks breaking above established resistance with
strong volume, in an established or improving trend.

**Screening rules:** - Daily close above 20-day EMA and 50-day EMA. -
Price breaks above the prior 20--30 trading-day resistance. - Breakout
volume is at least 1.5--2.0 times the 20-day average volume. - RSI(14)
is between 55 and 70. - Prefer stocks outperforming NIFTY 50. - Avoid
treating an intraday move as a confirmed daily-close breakout.

**Entry:** - Trigger above the breakout candle high, subject to a
configurable entry buffer. - Prefer a successful retest of the breakout
level when the setup remains valid. - Do not chase an entry if the price
has moved too far from the planned trigger or the reward/risk ratio has
deteriorated.

**Stop-loss:** - Below the breakout level or the most recent meaningful
swing low, whichever gives a technically valid and risk-appropriate
stop. - Use the wider structural stop only if position sizing remains
within the risk budget.

**Target and management:** - Initial target: 2R--3R, where R is the
initial per-share risk. - Consider partial profit-taking and a trailing
stop after the trade moves in favour. - Invalidate the setup if price
closes back below the breakout level with material selling pressure,
according to configurable rules.

### Strategy 2 --- 20 EMA pullback

**Concept:** Buy a controlled pullback in a well-established uptrend.

**Screening rules:** - 50-day EMA above 200-day EMA. - Price above the
50-day EMA. - Price retraces towards the 20-day EMA without materially
damaging the trend. - RSI(14) cools towards approximately 40--50. - A
bullish reversal candle forms near the 20 EMA or another defined support
area. - Recovery-candle volume increases relative to recent sessions.

**Entry:** - Above the high of the confirmed bullish reversal candle. -
Avoid entry before the reversal candle is complete.

**Stop-loss:** - Below the recent swing low or the technical
invalidation level.

**Target and management:** - First target: previous swing high. - If
momentum continues, seek at least 2R where practical. - Trail only
according to a defined rule; do not widen a stop to accommodate a losing
trade.

### Strategy 3 --- Relative strength

**Concept:** Prefer stocks that are outperforming the broad market and
their sector.

**Screening rules:** - Stock outperforms NIFTY 50 over both 1-month and
3-month lookback periods. - Stock is above its 20-, 50-, and 200-day
EMAs. - Its sector index also outperforms NIFTY 50 over the selected
lookback periods. - Price structure shows higher highs and higher
lows. - Prefer strong delivery participation only when reliable delivery
data is available. - Calculate relative strength as a return comparison
or normalized ratio. Do not confuse this with RSI, the Relative Strength
Index.

**Entry:** - On a confirmed consolidation breakout or a controlled
pullback to support.

**Stop-loss:** - Below the most recent meaningful swing low or the
setup's technical invalidation level.

**Target and management:** - Trail using the 20-day EMA or a 2-ATR
trailing stop. - Make the selected trailing method explicit and test it;
do not switch methods opportunistically.

### Strategy 4 --- 52-week high momentum

**Concept:** Identify stocks demonstrating sustained demand by
approaching or breaking their 52-week highs.

**Screening rules:** - Price is within approximately 3--5% of its
52-week high, or is breaking to a new 52-week high. - Strong relative
strength against NIFTY 50. - Price above 50-day and 200-day EMAs. -
Volume expands on a breakout. - No known major deterioration in earnings
or business fundamentals. - Account for the effect of corporate actions
and missing history.

**Entry:** - On a confirmed breakout above the 52-week high or a
successful retest. - Avoid chasing an extended price move.

**Stop-loss:** - Below breakout support or the recent swing low.

**Target and management:** - Initial objective: 2R--3R. - Trail the stop
while the trend remains valid.

### Strategy 5 --- Earnings momentum

**Concept:** Combine improving company results with positive price and
volume confirmation.

**Screening rules:** - Prefer year-over-year revenue growth above 15%. -
Prefer year-over-year profit growth above 20%. - Improving operating
margins. - Positive earnings surprise, only where a reliable consensus
estimate is available. - Strong price reaction with above-average volume
after results. - Price sustains a post-results breakout or
consolidation. - Check for one-off gains, exceptional items, accounting
changes, debt stress, auditor qualifications, and other factors that may
make headline growth misleading.

**Entry:** - Wait for initial results-day volatility to settle. - Enter
only after price confirms strength through a defined breakout or
post-results consolidation pattern. - Do not assume that good reported
results automatically mean the stock is a good trade.

**Stop-loss:** - Below the post-results consolidation low or the setup's
technical invalidation level.

**Target and management:** - Initial objective: 2R--3R, or trail while
momentum remains strong.

## 4. Strategy priority and expected holding periods

Use the following initial priority order. These are research
preferences, not claims that one strategy will always outperform.

  Strategy                  Priority   Indicative holding period
  ----------------------- ---------- ---------------------------
  Momentum breakout                1          3--15 trading days
  20 EMA pullback                  2          3--12 trading days
  Relative strength                3          5--20 trading days
  52-week high momentum            4          5--20 trading days
  Earnings momentum                5          3--15 trading days

Holding periods are guidelines. Exit based on tested rules, stop-loss,
target, or setup invalidation rather than forcing a trade to remain open
for a predetermined number of days.

## 5. Market-regime filter

Before ranking individual stocks, classify the broad market as: -
**Bullish** - **Sideways / mixed** - **Bearish**

Use transparent, configurable evidence such as NIFTY 50 position
relative to its 20-, 50-, and 200-day EMAs, EMA slope, market breadth,
volatility, and sector participation.

The regime filter must: - Reduce or suppress long-entry signals when the
market is broadly bearish. - Allow more selective setups in sideways
conditions. - Prefer trend-following setups when the market and sector
trend support them. - Explain which indicators caused the regime
classification. - Avoid overfitting thresholds to a single historical
period.

Do not label a market regime with certainty. Include a regime confidence
or evidence summary.

## 6. Combined screening and ranking engine

Build one screening pipeline that combines the five strategies rather
than treating them as five unrelated stock lists.

### Pipeline

1.  **Universe selection:** Load current NIFTY 500 constituents and
    apply data-quality and liquidity checks.
2.  **Market-regime assessment:** Classify the broad market and relevant
    sector conditions.
3.  **Technical calculations:** Calculate EMA(20/50/200), RSI(14),
    ATR(14), rolling highs/lows, average volume, returns, relative
    strength, and other required indicators.
4.  **Strategy evaluation:** Evaluate each of the five strategy rule
    sets independently and record pass/fail/unknown for every condition.
5.  **Fundamental and event checks:** Apply available earnings,
    financial-quality, corporate-news, and regulatory-risk checks.
6.  **Risk and tradability checks:** Estimate entry, stop, target,
    reward/risk, position size, liquidity, and gap/event risk.
7.  **Ranking:** Score and rank eligible stocks using documented,
    configurable weights.
8.  **Shortlist:** Return the top 10 candidates, or fewer if fewer
    stocks pass minimum quality and risk rules.
9.  **Paper trading:** Track signals, simulated entries, exits,
    slippage, costs, and outcomes.
10. **Monitoring:** Re-evaluate open paper trades and alert when
    material news, technical invalidation, or risk changes occur.

### Initial scoring model

Implement an explainable 0--100 score. Treat these weights as an initial
hypothesis to validate, not as proven optimal weights.

  Component                          Initial weight
  -------------------------------- ----------------
  Momentum breakout                              25
  20 EMA pullback                                20
  Relative strength                              20
  52-week high momentum                          15
  Earnings momentum                              10
  Market/sector regime alignment                 10
  **Total**                                 **100**

Scoring requirements: - A strategy score must reflect how many of its
required conditions pass and the quality of the signal. - A stock can
receive credit from multiple strategies, but avoid double-counting
highly correlated indicators. - Missing data must not be silently
treated as a pass. - A fundamental or event-risk veto may disqualify a
stock regardless of its technical score. - A stock failing minimum
liquidity, data-quality, or reward/risk requirements must not enter the
final shortlist. - Keep raw strategy scores, component scores, veto
reasons, and final score visible. - Make all thresholds and weights
configurable and versioned.

### Output: daily top-10 shortlist

For each candidate, provide: - Rank and symbol. - Company name and
sector. - Last available close and market-data timestamp. - Strategy or
strategies triggered. - Market-regime alignment. - Technical evidence:
EMA positions, RSI, relative strength, breakout level, volume ratio,
52-week-high distance, as applicable. - Fundamental/event summary and
data freshness. - Proposed entry trigger. - Stop-loss and rationale. -
Target 1 and target 2, where applicable. - Per-share risk and
reward/risk ratio. - Suggested quantity based on configured capital and
risk limits. - Score out of 100 and a confidence label with reasons. -
Key risks, invalidation conditions, and missing data. - Clear status:
**Watch**, **Paper-entry eligible**, **Avoid**, or **Existing position
alert**.

Do not force the engine to return 10 stocks. If only three qualify,
return three and explain why.

## 7. Capital protection and position sizing

Capital preservation takes precedence over signal frequency.

Use these initial portfolio constraints, all configurable: - Risk per
trade: 0.5--1.0% of trading capital; default to 0.75% for initial
paper-trading examples. - Minimum planned reward/risk: 1:2, unless a
separately validated strategy configuration explicitly justifies another
threshold. - Maximum open positions: 5--8; default 5 for initial paper
trading. - Maximum sector exposure: 25% of portfolio market value or
risk budget, with the exact definition configurable and clearly
reported. - Open new long trades only when permitted by the
market-regime and portfolio-risk rules. - Never average down or add to a
losing position. - Every trade must have a stop-loss or a clearly
documented exit/invalidation rule. - Do not exceed available capital,
liquidity, or concentration limits. - Account for correlation between
positions; multiple stocks in the same sector may represent concentrated
risk. - Include brokerage, taxes/fees where applicable, spread,
slippage, and realistic execution assumptions in backtests and
paper-trading reports. - Explicitly disclose that overnight gaps can
cause actual losses to exceed the planned stop-loss.

### Position-size calculation

Use:

`Risk amount = Trading capital × Risk percentage`

`Risk per share = Entry price − Stop-loss price` for a long trade.

`Quantity = floor(Risk amount / Risk per share)`

Then reduce quantity if required by available capital, liquidity, sector
exposure, total portfolio risk, or other constraints. Include expected
costs and slippage in the final risk estimate.

### Worked example

Assume: - Trading capital: ₹5,00,000 - Risk per trade: 0.75% - Entry
price: ₹1,000 - Stop-loss: ₹975 - Risk per share: ₹25

Then: - Planned risk amount: ₹5,00,000 × 0.75% = **₹3,750** - Quantity:
₹3,750 / ₹25 = **150 shares** - Target at 1:2 reward/risk: **₹1,050** -
Planned gross loss if stopped at ₹975: **₹3,750** - Planned gross profit
at ₹1,050: **₹7,500**

This example excludes costs and slippage. Actual losses may exceed the
planned amount, particularly after overnight gaps.

## 8. Backtesting and validation --- mandatory before real capital

Do not present a strategy as reliable or profitable merely because its
rules sound reasonable. Test every strategy separately and test the
combined ranking engine.

### Backtesting requirements

-   Use point-in-time data and historical NIFTY 500 membership where
    available.
-   Avoid look-ahead bias: a signal may use only information available
    at the decision time.
-   Avoid survivorship bias.
-   Use adjusted data and correctly handle corporate actions.
-   Model next-session execution realistically; do not assume fills at a
    price that was not executable after the signal became known.
-   Include brokerage, taxes/fees, spread, slippage, liquidity
    constraints, and gap risk as appropriate.
-   Prevent duplicate or overlapping positions from exceeding portfolio
    constraints.
-   Use the same position-sizing and exit rules intended for paper
    trading.
-   Report results by strategy, market regime, sector, year, and
    combined portfolio.

### Required metrics

-   Number of trades.
-   Win rate.
-   Average win and average loss.
-   Expectancy per trade.
-   Profit factor.
-   Maximum drawdown.
-   CAGR or annualized return, where appropriate.
-   Sharpe and Sortino ratios, with methodology stated.
-   Average holding period.
-   Exposure and turnover.
-   Largest loss and gap-loss examples.
-   Performance after all assumed costs.
-   Benchmark comparison against an appropriate passive alternative.

Win rate alone is not sufficient. A strategy with a lower win rate can
still have positive expectancy if average wins sufficiently exceed
average losses; the reverse is also true.

### Validation process

1.  Develop rules on a training period.
2.  Tune parameters only within that period using a documented process.
3.  Validate on a separate, unseen out-of-sample period.
4.  Use walk-forward testing across multiple market regimes.
5.  Perform sensitivity analysis on thresholds, costs, and slippage.
6.  Check whether results depend excessively on a small number of
    trades, stocks, or market periods.
7.  Freeze a version of the rules before paper trading.
8.  Run paper trading long enough to collect a meaningful sample across
    different market conditions.
9.  Compare paper results with backtest expectations and investigate
    discrepancies.
10. Do not enable live trading automatically after a backtest or
    paper-trading milestone.

Never claim that any strategy "never fails," "cannot lose," or
guarantees returns.

## 9. News, earnings, and risk interpretation

For company, sector, market, policy, and regulatory news: - Identify the
affected company, sector, index, or portfolio holding. - Distinguish
confirmed facts from interpretation, speculation, and unverified
reports. - Record source, publication time, event time, and retrieval
time. - Assess likely direction, potential materiality, time horizon,
and uncertainty. - Check whether the information was already reflected
in the price before treating it as a new catalyst. - Explain the
evidence linking the event to the stock. - Flag results dates, corporate
actions, regulatory decisions, litigation, governance concerns, and
other material events. - Never convert a headline into an automatic
buy/sell instruction without corroborating evidence and risk checks.

## 10. Architecture and implementation guidance

Design the engine as modular components so each can be tested and
replaced independently:

-   **Universe service:** NIFTY 500 constituents, sector mapping,
    liquidity eligibility.
-   **Market-data adapter:** Licensed market-data providers and
    normalized OHLCV.
-   **Fundamental/event adapters:** Financial results, corporate
    announcements, news, and regulatory sources.
-   **Data-quality service:** Missing bars, stale prices, outliers,
    duplicates, corporate actions, and source lineage.
-   **Indicator service:** EMA, RSI, ATR, returns, relative strength,
    rolling highs/lows, volume statistics.
-   **Market-regime service:** Index trend, breadth, volatility, and
    sector regime.
-   **Strategy modules:** Five independent strategy evaluators.
-   **Fundamental/risk filter:** Financial deterioration and event-risk
    checks.
-   **Ranking service:** Weighted, explainable scores and veto handling.
-   **Trade-planning service:** Entry, stop, targets, quantity,
    reward/risk, and portfolio constraints.
-   **Backtesting service:** Historical simulation with realistic costs
    and bias controls.
-   **Paper-trading service:** Simulated orders, fills, positions, P&L,
    and audit trail.
-   **Alert service:** New candidates, setup invalidation, stop/target
    events, and material news.
-   **Observability and audit:** Logs, metrics, data timestamps,
    strategy versions, and decision explanations.

Keep business rules and thresholds in configuration, not scattered
through code. Version every strategy and scoring-model change. Make all
signal calculations deterministic and reproducible for a given dataset
and configuration.

## 11. Safety, compliance, and user-facing language

-   This is a decision-support and research system, not a
    guaranteed-profit system.
-   Do not provide certainty about future prices.
-   Do not fabricate exchange data, news, financials, or backtest
    results.
-   Clearly label delayed, stale, estimated, or missing data.
-   Do not use restricted data feeds or violate exchange/vendor
    licensing terms.
-   Do not execute trades or connect to a live broker for order
    placement unless separately and explicitly authorized through a
    future scope.
-   Present trade ideas as conditional plans, not commands.
-   State that stop orders may execute at materially different prices
    during gaps or illiquid conditions.
-   Keep an audit record of input data, timestamps, rule version, score,
    and reasons for each candidate.

## 12. Delivery sequence

Implement in small, verifiable increments:

1.  Document the data-source strategy and licensing constraints.
2.  Define the canonical market-data and instrument schemas.
3.  Build NIFTY 500 universe and data-quality validation.
4.  Implement and unit-test technical indicators.
5.  Implement each of the five strategy modules independently.
6.  Implement market-regime and sector-relative-strength calculations.
7.  Implement fundamental/event filters with explicit unknown-data
    handling.
8.  Implement explainable ranking and top-10 reporting.
9.  Implement risk sizing and portfolio constraints.
10. Build a backtesting harness and validate for bias and realistic
    costs.
11. Build paper-trading lifecycle, alerts, and audit trail.
12. Produce daily reports and performance dashboards.
13. Review results and assumptions before considering any future
    live-trading scope.

For every increment, provide: - Design decisions and assumptions. - Data
dependencies and limitations. - Implementation and tests. - Example
input and output. - Failure cases and operational considerations. -
Evidence that calculations are correct.

## 13. Daily report format

The daily report should begin with:

1.  Report date/time and data freshness.
2.  NIFTY 50 market regime and evidence.
3.  Sector strength/weakness summary.
4.  Number of NIFTY 500 stocks scanned and excluded, with reasons.
5.  Top 10 ranked candidates (or fewer if insufficient qualifying
    setups).
6.  For each candidate: triggered strategies, score, evidence, entry,
    stop, targets, quantity, reward/risk, and risks.
7.  Existing paper-position alerts.
8.  Upcoming earnings and material event risks.
9.  Data-quality warnings and unavailable feeds.
10. A clear statement that the report is research/paper-trading output,
    not a guaranteed prediction.

**Primary product objective:** Build a robust, explainable, and testable
NIFTY 500 multi-strategy swing-trading scanner for AuraTrade. Optimize
for risk-adjusted performance and decision quality, not the number of
signals or headline backtest returns.

---

# 14. Strategy Management Studio — configurable strategy lifecycle

## 14.1 Core design principle

Strategies must be **first-class, independently managed platform entities**. Do not hardcode the five initial strategies into the scanner or ranking engine.

Build a Strategy Management Studio that allows an authorized user to create, configure, version, test, activate, pause, disable, archive, compare, and monitor strategies through the AuraTrade UI and APIs.

The initial five strategies are seed configurations:
1. Momentum breakout
2. 20 EMA pullback
3. Relative strength
4. 52-week high momentum
5. Earnings momentum

The architecture must support adding further strategies without changing the core scanner, ranking engine, or trade-planning service.

Separate these concepts:
- **Strategy definition:** The reusable logic, indicators, conditions, parameters, and entry/exit rules.
- **Strategy instance/configuration:** A specific configured version of a strategy, including thresholds, universe, timeframe, and risk settings.
- **Strategy activation:** Whether that version is enabled for a particular screening profile.
- **Strategy evaluation:** The result of applying a specific version to a stock at a specific time.
- **Strategy performance:** Backtest and paper-trading outcomes for that exact version.
- **Scoring profile:** The combination of active strategies and their weights used to rank stocks.

## 14.2 Strategy lifecycle and controls

Provide the following controls for each strategy:

| Control | Expected behaviour |
|---|---|
| Enable | Include the strategy in the selected scoring profile and screening runs |
| Disable | Exclude it from new screening/ranking runs without deleting its history |
| Pause | Temporarily stop new signals while preserving open paper-trade monitoring |
| Configure | Change parameters, filters, timeframes, and thresholds |
| Weightage | Set its contribution to a combined score |
| Duplicate | Create a new strategy configuration from an existing one |
| Add strategy | Register a new strategy through a supported strategy interface |
| Backtest | Run historical validation for a selected version |
| Paper trade | Enable signal simulation for a selected version |
| Compare | Compare strategy performance with other strategies or versions |
| Version history | Review previous configurations and their performance |
| Rollback | Restore a prior approved version as a new active version |
| Archive | Retain history but remove the strategy from normal selection |

Disabling or pausing a strategy must not silently close existing paper positions. Existing positions must continue to be monitored using the exit rules and version that opened them, unless an explicit, audited position-management action is taken.

Every configuration change must be versioned, timestamped, and attributable to an authorized user or system process.

## 14.3 Strategy configuration model

At minimum, each strategy configuration should support:

- Unique strategy ID and human-readable name.
- Description, category, and investment thesis.
- Strategy version and status.
- Asset universe, such as NIFTY 500 or a selected watchlist.
- Market, exchange, and sector scope.
- Timeframe: daily, weekly, or other supported interval.
- Required indicators and their parameters.
- Entry conditions and their logical operators.
- Exit conditions and invalidation rules.
- Stop-loss and target methodology.
- Volume and liquidity requirements.
- Fundamental and event filters.
- Market-regime compatibility.
- Weight in each scoring profile.
- Minimum strategy score required to qualify.
- Backtest configuration and benchmark.
- Paper-trading configuration.
- Data dependencies and data freshness requirements.
- Created by, approved by, created at, and effective from.
- Change reason and version notes.

Conditions should support AND/OR groups, comparisons, ranges, crossovers, lookback windows, and explicitly defined missing-data behaviour. Do not allow arbitrary executable code from the UI. Use a validated rule schema or a sandboxed, reviewed strategy implementation interface.

Example conceptual configuration:

```yaml
strategy_id: momentum_breakout
name: Momentum Breakout
version: 1.0.0
status: enabled
universe: NIFTY_500
timeframe: 1d
weight: 25
minimum_score: 65

conditions:
  - indicator: close
    operator: greater_than
    reference: ema_20
  - indicator: close
    operator: greater_than
    reference: ema_50
  - indicator: breakout_volume_ratio
    operator: greater_than_or_equal
    value: 1.5
  - indicator: rsi_14
    operator: between
    min: 55
    max: 70

entry:
  type: breakout_high
  confirmation: daily_close

risk:
  stop_method: breakout_level_or_swing_low
  target_r_multiple: 2.0
```

This is an illustrative schema. Define and validate the actual schema before implementation.

## 14.4 Strategy score versus combined stock score

This is a mandatory product capability. Always show both individual strategy results and the combined result.

For every stock, show:
- Individual score for every strategy, including disabled strategies when historical or diagnostic evaluation is available.
- Whether the strategy is enabled in the selected profile.
- Whether the stock passed, partially passed, failed, or could not be evaluated.
- Strategy contribution to the combined score.
- Combined score under the selected profile.
- Rank under that profile.
- Any hard exclusion or risk veto.

Example display (illustrative values only):

| Strategy | Individual score | Status | Weight | Weighted contribution |
|---|---:|---|---:|---:|
| Momentum breakout | 88 | Pass | 25% | 22.00 |
| 20 EMA pullback | 72 | Pass | 20% | 14.40 |
| Relative strength | 91 | Pass | 20% | 18.20 |
| 52-week high | 80 | Pass | 15% | 12.00 |
| Earnings momentum | 40 | Fail | 10% | 0.00 |
| Regime alignment | 90 | Pass | 10% | 9.00 |
| **Combined score** | | | **100%** | **75.60** |

The example uses a zero contribution for a failed strategy. Define this behaviour consistently and make it visible. Do not silently remove failed strategy weights and renormalize the remaining weights unless the selected scoring profile explicitly specifies that policy.

### Recommended score calculation

For active strategies with weights summing to 100:

`Combined score = sum(strategy score × strategy weight) / 100`

If a strategy is disabled, its weight must be removed from the active profile and the remaining weights must be handled by an explicit profile policy:
- **Strict weights:** Preserve configured weights; the total active weight may be below 100 and the score is normalized against the active weight only if the profile explicitly says so.
- **Auto-normalized weights:** Rescale enabled strategy weights proportionally to total 100.
- **Fixed contribution:** Keep disabled strategy contribution at zero and do not redistribute its weight.

Support these as explicit scoring policies. Show the effective weights used for every run.

Distinguish:
- **Strategy score:** How well the stock matches one strategy.
- **Combined score:** Weighted aggregate across strategies.
- **Confidence:** Quality, completeness, freshness, and agreement of the evidence.
- **Trade quality:** Whether entry, stop, target, liquidity, and portfolio constraints make the setup actionable.

A high combined score must not automatically mean a high-confidence or executable trade.

## 14.5 Stock-level strategy matrix

Provide a matrix/table view across the selected universe:

| Stock | Breakout | EMA pullback | Relative strength | 52-week high | Earnings | Combined | Rank |
|---|---:|---:|---:|---:|---:|---:|---:|
| Stock A | 88 | 72 | 91 | 80 | 40 | 75.6 | 1 |
| Stock B | 65 | 90 | 82 | 70 | 75 | 76.0 | 2 |

Use actual computed values only in production. The sample above is illustrative and does not represent live market data.

Allow users to:
- Sort by any individual strategy score.
- Sort by combined score.
- Filter by strategy pass/fail.
- Filter by sector, market cap, liquidity, and market regime.
- Select a stock and inspect its complete strategy evaluation.
- Compare two or more stocks side by side.
- View how ranks change when a strategy is enabled, disabled, or reweighted.
- Export results with score explanations and data timestamps.

## 14.6 Multiple scoring profiles and what-if analysis

Support multiple named scoring profiles, for example:
- Balanced Swing
- Breakout Focus
- Trend Following
- Conservative
- High Relative Strength
- Earnings Catalyst
- Experimental

Each profile has its own:
- Enabled strategies.
- Strategy weights.
- Minimum strategy scores.
- Market-regime rules.
- Liquidity and fundamental filters.
- Risk constraints.
- Ranking and tie-break rules.

Provide a what-if simulator so a user can adjust weights or toggle strategies and immediately see:
- Revised stock scores.
- Revised top-10 shortlist.
- Stocks entering or leaving the shortlist.
- Rank movement.
- Strategy contributions before and after.
- Whether the change increases concentration in a sector or correlated group.

What-if changes must be simulations until explicitly saved and activated. Never silently alter the production profile.

## 14.7 Strategy creation and extensibility

Design a stable strategy plugin/interface contract. A new strategy should provide:
- Metadata and version.
- Required data fields and indicators.
- Parameter schema and validation.
- Evaluation logic.
- Signal explanation.
- Entry, stop, target, and invalidation outputs where applicable.
- Unit tests and sample fixtures.
- Backtest compatibility.
- Paper-trading compatibility.
- Resource and execution limits.

Support two controlled ways to add strategies:
1. **Rule-builder strategies:** Built from approved indicators, operators, conditions, and templates.
2. **Code-based strategies:** Implemented by developers through a versioned interface, reviewed, tested, and registered.

Potential future strategies to evaluate—not assume profitable—include:
- MACD trend continuation or crossover.
- RSI divergence and reversal.
- Bollinger Band squeeze breakout.
- Donchian channel breakout.
- ATR volatility contraction and expansion.
- Moving-average crossover.
- Volume-price trend and accumulation.
- Sector rotation and sector leadership.
- Gap-and-go or post-gap continuation.
- Base breakout and consolidation patterns.
- Mean reversion, only in suitable market regimes.
- Multi-timeframe trend alignment.
- Market breadth and advance/decline participation.

Do not add all strategies indiscriminately. Each new strategy must have a clear hypothesis, avoid duplicating existing signals, and pass independent validation before inclusion in a production scoring profile.

## 14.8 Strategy correlation and redundancy analysis

Several strategies may select the same stocks because they use related momentum and trend indicators. This can create false confidence if their scores are simply added.

Build a strategy-correlation module that measures:
- Overlap in selected stocks.
- Correlation of daily or weekly strategy returns.
- Similarity of entry dates and exit dates.
- Performance by market regime.
- Incremental contribution of each strategy to the combined portfolio.
- Whether a strategy improves risk-adjusted results or merely duplicates another strategy.

Display a strategy overlap matrix and correlation heatmap. Flag highly correlated strategies and allow users to inspect the effect of reducing their combined influence.

Use out-of-sample results for meaningful comparisons. Correlation is descriptive and does not prove that strategies will remain correlated.

## 14.9 Strategy performance dashboard

Track every strategy version independently across backtests and paper trading.

Show:
- Total signals and executed paper trades.
- Win rate.
- Average win/loss and expectancy.
- Profit factor.
- Maximum drawdown.
- Average holding period.
- Return after costs.
- Slippage sensitivity.
- Performance by year and market regime.
- Performance by sector and market capitalization.
- Signal-to-entry conversion.
- Stop-loss and target hit rates.
- Number of missed or invalidated signals.
- Comparison with NIFTY 50 or a relevant benchmark.
- Data-quality failures and skipped evaluations.

Show both absolute performance and incremental contribution to the combined portfolio. Do not rank strategies by win rate alone.

Provide a strategy health status such as:
- Research
- Backtest passed
- Out-of-sample validated
- Paper trading
- Active
- Under review
- Suspended
- Archived

These statuses must be based on explicit, configurable governance criteria, not arbitrary labels.

## 14.10 Strategy governance and promotion workflow

Use a controlled lifecycle:

`Draft → Research → Backtesting → Out-of-sample validation → Paper trading → Approved → Active`

A strategy may also move to:
- Rejected
- Suspended
- Archived

Promotion requirements should include:
- Documented investment hypothesis.
- Reviewed strategy logic and parameters.
- Unit and integration tests.
- Backtest report with costs and bias controls.
- Out-of-sample and walk-forward results.
- Paper-trading evidence.
- Risk review.
- Approval and version freeze.

A change to a strategy's rules, indicators, weights, or exits creates a new version. Do not overwrite the configuration used by historical backtests or existing paper positions.

Support role-based permissions:
- Viewer: Read dashboards and results.
- Researcher: Create and test draft strategies.
- Strategy manager: Configure profiles and manage approved strategies.
- Approver: Approve promotion to active status.
- Administrator: Manage platform configuration and access.

For an individual-user initial deployment, these roles can be simplified, but the authorization model should remain extensible.

---

# 15. Additional AuraTrade platform capabilities

Treat the following as a product roadmap. Build the foundation first and add capabilities incrementally.

## 15.1 Portfolio-aware intelligence

Allow users to maintain one or more portfolios with:
- Holdings, quantity, average cost, and acquisition date.
- Available cash and portfolio value.
- Sector and stock concentration.
- Realized and unrealized P&L.
- Planned entry, stop, and target.
- Strategy and signal that motivated the position.

The screening engine should distinguish:
- New buy candidate.
- Existing holding with positive momentum.
- Existing holding with weakening technical conditions.
- Position approaching stop-loss.
- Position with a potential exit or reduction signal.
- Stock already held, where a duplicate entry may be inappropriate.

Do not automatically recommend selling merely because one strategy score declines. Evaluate the original trade thesis, current risk, event information, portfolio context, and exit rules.

## 15.2 Market and sector intelligence

Create a market intelligence layer that monitors:
- NIFTY 50, NIFTY 500, and relevant sector indices.
- Market breadth and participation.
- Sector rotation and leadership changes.
- Volatility and gap behaviour.
- Major domestic and global market events.
- RBI, government, SEBI, and other relevant regulatory or policy announcements.

Connect market and sector context to stock signals. Explain whether a stock's move appears company-specific, sector-driven, or broad-market-driven.

## 15.3 News-to-stock impact engine

Build an event pipeline that:
1. Collects news and official announcements from permitted sources.
2. Deduplicates related stories.
3. Extracts companies, sectors, people, policies, and event types.
4. Links entities to listed securities.
5. Classifies event type and likely materiality.
6. Assesses potential positive, negative, or uncertain impact.
7. Checks the stock's existing price and volume reaction.
8. Links the event to affected portfolio holdings and watchlist stocks.
9. Creates an explainable alert for human review.

Every alert should show source, timestamp, evidence, confidence, affected securities, possible impact horizon, and uncertainty. An LLM may assist with extraction and explanation, but deterministic rules should govern risk limits and trade eligibility.

## 15.4 Watchlists and discovery

Support:
- Custom watchlists.
- Sector watchlists.
- Strategy-specific watchlists.
- Stocks near entry triggers.
- Stocks that almost qualified.
- Excluded stocks with reasons.
- Recently added and removed candidates.
- User notes and thesis tracking.

An "almost qualified" list is useful because users can see which condition prevented a stock from qualifying and what needs to change.

## 15.5 Alerts and notification centre

Provide configurable alerts for:
- New strategy signal.
- Stock entering or leaving top 10.
- Entry trigger reached.
- Breakout failure.
- Stop-loss or target reached.
- Relative-strength deterioration.
- Unusual volume.
- Earnings announcement or result.
- Material company or regulatory news.
- Portfolio concentration or risk-limit breach.
- Stale or unavailable data feed.

Support in-app notifications first. Add email, mobile push, or messaging integrations later. Prevent duplicate alerts and provide severity, expiry, acknowledgement, and audit history.

## 15.6 Trade journal and learning loop

For every paper trade, capture:
- Why the stock was selected.
- Strategy and strategy version.
- Individual and combined scores at entry.
- Planned entry, stop, target, and quantity.
- Actual simulated fill and costs.
- Exit reason and outcome.
- Maximum favourable and adverse excursion.
- Whether the rules were followed.
- Notes and post-trade review.

Use the journal to identify systematic weaknesses. Do not automatically modify strategy parameters from recent wins or losses. Any adaptive change must go through controlled research, out-of-sample validation, and version approval.

## 15.7 Explainable AI research assistant

Add an AuraTrade assistant that can answer questions such as:
- Why is this stock ranked first today?
- Which strategies selected this stock?
- Why did this stock fail the breakout strategy?
- What happens if I disable earnings momentum?
- Which strategy performed best in bearish markets?
- Which stocks have the strongest relative strength this month?
- What changed in my portfolio after today's market news?
- Compare the current strategy version with the previous version.

The assistant must retrieve actual platform data and cite the relevant data timestamps, strategy versions, and evidence. It must not invent indicators, financial results, or backtest metrics.

Use an LLM for natural-language interaction, summarization, and research assistance—not as the source of truth for numeric calculations, strategy execution, or portfolio risk controls.

## 15.8 Data quality and source health centre

Provide a dashboard for:
- Feed availability and latency.
- Last successful update.
- Missing or stale OHLCV bars.
- Duplicate or outlier records.
- Corporate-action processing status.
- Fundamental data freshness.
- News-source health.
- Symbol mapping failures.
- Licensing and source restrictions.

If critical data is stale or missing, block affected signals or mark them as non-actionable. Never quietly calculate a current trade plan using stale data.

## 15.9 Research lab and experiment tracking

Allow researchers to run controlled experiments:
- Compare indicator periods.
- Compare entry and exit rules.
- Test different volume thresholds.
- Test different reward/risk targets.
- Compare market-regime filters.
- Compare scoring weights.
- Compare current and proposed strategy versions.

Record dataset version, date range, universe definition, parameters, costs, code version, and results for every experiment. Keep training, validation, and test periods separate. Avoid selecting a model solely because it performed best across many parameter combinations.

## 15.10 Risk cockpit

Provide a consolidated view of:
- Total planned portfolio risk.
- Risk per position.
- Sector concentration.
- Correlated holdings.
- Cash utilization.
- Exposure by strategy.
- Exposure by market regime.
- Upcoming earnings and event concentration.
- Gap and liquidity risk.
- Portfolio drawdown and risk-limit breaches.

The risk cockpit should be able to veto new paper entries even when their individual stock scores are high.

---

# 16. Recommended application navigation

Use a modular navigation structure:

1. **Market Overview** — indices, regime, breadth, sectors, volatility.
2. **Stock Scanner** — NIFTY 500 scan, top 10, filters, stock-level strategy matrix.
3. **Strategy Studio** — strategy catalogue, enable/disable, configuration, weights, versions.
4. **Scoring Profiles** — create and compare combined scoring profiles and run what-if analysis.
5. **Research Lab** — backtests, experiments, strategy correlation, validation reports.
6. **Paper Trading** — simulated orders, positions, P&L, trade lifecycle.
7. **Portfolio** — holdings, risk, allocation, performance, alerts.
8. **Market Intelligence** — company news, sector news, policy and regulatory events.
9. **Watchlists** — custom and strategy-specific lists.
10. **Alerts Centre** — signal, risk, data, and news notifications.
11. **Trade Journal** — decisions, outcomes, and post-trade reviews.
12. **Data Health** — feeds, freshness, quality, source lineage.
13. **AI Research Assistant** — natural-language queries over verified platform data.
14. **Administration** — users, permissions, configuration, audit, and integrations.

---

# 17. Core domain entities and auditability

Design the data model to support at least these entities:

- `StrategyDefinition`
- `StrategyVersion`
- `StrategyParameter`
- `StrategyCondition`
- `ScoringProfile`
- `ScoringProfileStrategy`
- `StrategyEvaluation`
- `StockScreeningRun`
- `StockScore`
- `MarketRegimeSnapshot`
- `BacktestRun`
- `BacktestTrade`
- `PaperOrder`
- `PaperPosition`
- `Portfolio`
- `PortfolioHolding`
- `RiskSnapshot`
- `MarketEvent`
- `NewsArticle`
- `Watchlist`
- `Alert`
- `TradeJournalEntry`
- `DataSource`
- `DataQualityEvent`
- `ApprovalRecord`
- `AuditEvent`

Every screening result must be reproducible from:
- The exact instrument universe and membership snapshot.
- Market and fundamental data snapshot or immutable data references.
- Data timestamps and source identifiers.
- Strategy version IDs.
- Scoring profile version.
- Market-regime snapshot.
- Calculation and application version.
- Risk configuration.

Store the individual strategy evaluations separately from the aggregate stock score. This enables historical re-ranking under a different scoring profile without losing the original evaluation evidence, provided the underlying data and evaluation versions are retained.

---

# 18. Suggested API capability boundaries

Design APIs around domain responsibilities. Names below are illustrative and should be aligned with the existing AuraTrade API conventions.

### Strategy management
- `GET /api/strategies`
- `POST /api/strategies`
- `GET /api/strategies/{strategyId}`
- `POST /api/strategies/{strategyId}/versions`
- `POST /api/strategies/{strategyId}/enable`
- `POST /api/strategies/{strategyId}/disable`
- `POST /api/strategies/{strategyId}/pause`
- `GET /api/strategies/{strategyId}/performance`

### Scoring profiles
- `GET /api/scoring-profiles`
- `POST /api/scoring-profiles`
- `PUT /api/scoring-profiles/{profileId}`
- `POST /api/scoring-profiles/{profileId}/simulate`
- `POST /api/scoring-profiles/{profileId}/activate`
- `GET /api/scoring-profiles/{profileId}/history`

### Screening and stock evaluations
- `POST /api/screening-runs`
- `GET /api/screening-runs/{runId}`
- `GET /api/stocks/{symbol}/strategy-evaluations`
- `GET /api/stocks/{symbol}/score-breakdown`
- `GET /api/screening-runs/{runId}/rankings`

### Research and paper trading
- `POST /api/backtests`
- `GET /api/backtests/{backtestId}`
- `POST /api/paper-trades`
- `GET /api/paper-positions`
- `GET /api/portfolios/{portfolioId}/risk`

All configuration-changing endpoints must enforce authorization, validation, optimistic concurrency where appropriate, and audit logging. Activating a profile or strategy version should be an explicit action, not an incidental side effect of editing it.

---

# 19. Recommended implementation priorities

Do not attempt to build every capability at once. Prioritize a dependable foundation.

| Phase | Scope | Outcome |
|---|---|---|
| 1 | Data model, NIFTY 500 universe, data quality, indicators | Reliable screening foundation |
| 2 | Five strategy modules and independent evaluations | Reusable strategy engine |
| 3 | Strategy Studio, enable/disable, parameters, versions | User-managed strategies |
| 4 | Scoring profiles, weights, individual and combined scores | Configurable ranking |
| 5 | Stock matrix, top-10 UI, explanations, what-if simulation | Usable research experience |
| 6 | Backtesting, out-of-sample validation, correlation analysis | Evidence-based strategy selection |
| 7 | Paper trading, trade journal, portfolio risk | End-to-end simulated trading |
| 8 | News intelligence, alerts, AI research assistant | Context-aware decision support |
| 9 | Advanced experiments, additional strategies, integrations | Extensible platform |

For the first usable release, the must-have capabilities are:
1. Five independent strategy evaluators.
2. Enable/disable controls.
3. Editable strategy weights.
4. Multiple scoring profiles.
5. Individual score and combined score per stock.
6. Strategy contribution breakdown.
7. Top-10 ranking and stock-level explanation.
8. Historical strategy/version tracking.
9. Backtesting and paper-trading foundation.
10. Risk and data-quality vetoes.

---

# 20. Additional instructions to Claude

Before implementing these capabilities:

1. Inspect the existing AuraTrade codebase, architecture, database, APIs, and UI. Do not assume a greenfield project.
2. Produce a gap analysis mapping existing components to the target capabilities.
3. Propose a modular architecture and domain model before making broad code changes.
4. Identify which features belong in deterministic services and which benefit from LLM-based assistance.
5. Keep strategy logic independent from strategy configuration, scoring profiles, and UI.
6. Store strategy evaluations independently so different profiles can rank the same stock universe without recalculating or corrupting the original evaluation.
7. Make enable/disable, weight changes, profile activation, and strategy version promotion auditable.
8. Provide a preview/dry-run before activating a changed profile.
9. Ensure an active paper position retains the strategy version and exit policy that created it.
10. Never use an LLM to invent or directly calculate authoritative prices, indicators, scores, quantities, or P&L.
11. Build tests for score calculations, weight normalization, disabled strategies, missing data, veto rules, version changes, and reproducibility.
12. Do not claim a strategy is profitable or production-ready without the required validation evidence.
13. Deliver the work phase by phase, with working tests and documentation at every stage.

**Product vision:** AuraTrade should let the user define, combine, test, compare, and govern multiple investment strategies while providing transparent stock-level evidence, portfolio-aware risk controls, market intelligence, and explainable decision support. The platform—not a fixed set of hardcoded indicators—must own the strategy lifecycle.
