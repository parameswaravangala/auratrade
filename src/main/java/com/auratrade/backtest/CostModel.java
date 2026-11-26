package com.auratrade.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Trading costs for NSE equity delivery. Defaults are Zerodha's published charges, read 2026-11-26
 * at https://zerodha.com/charges (a broker's schedule; not checked against NSE, SEBI or the Stamp
 * Act). Every rate is a parameter so it can be updated when charges change.
 *
 * @param brokeragePerOrder flat brokerage per order in rupees (0 for delivery)
 * @param sttRate securities transaction tax, buy and sell (0.001 = 0.1%)
 * @param transactionRate exchange transaction charge, buy and sell (0.0000307 = 0.00307%)
 * @param sebiRate SEBI fee, buy and sell (INR 10 per crore = 0.000001)
 * @param stampBuyRate stamp duty on buys only (0.00015 = 0.015%)
 * @param gstRate GST on brokerage, transaction charge and SEBI fee (0.18)
 * @param dpChargePerSell depository charge per stock sold, in rupees (15.34)
 */
public record CostModel(
    BigDecimal brokeragePerOrder,
    BigDecimal sttRate,
    BigDecimal transactionRate,
    BigDecimal sebiRate,
    BigDecimal stampBuyRate,
    BigDecimal gstRate,
    BigDecimal dpChargePerSell) {

  public static CostModel zerodhaDelivery() {
    return new CostModel(
        BigDecimal.ZERO,
        new BigDecimal("0.001"),
        new BigDecimal("0.0000307"),
        new BigDecimal("0.000001"),
        new BigDecimal("0.00015"),
        new BigDecimal("0.18"),
        new BigDecimal("15.34"));
  }

  public static CostModel free() {
    BigDecimal z = BigDecimal.ZERO;
    return new CostModel(z, z, z, z, z, z, z);
  }

  /** Charges on a buy of {@code value} rupees, rounded to paise. */
  public BigDecimal buyCost(BigDecimal value) {
    return common(value).add(value.multiply(stampBuyRate)).setScale(2, RoundingMode.HALF_UP);
  }

  /** Charges on a sell of {@code value} rupees, rounded to paise. */
  public BigDecimal sellCost(BigDecimal value) {
    return common(value).add(dpChargePerSell).setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal common(BigDecimal value) {
    BigDecimal txn = value.multiply(transactionRate);
    BigDecimal sebi = value.multiply(sebiRate);
    BigDecimal gst = brokeragePerOrder.add(txn).add(sebi).multiply(gstRate);
    return brokeragePerOrder.add(value.multiply(sttRate)).add(txn).add(sebi).add(gst);
  }
}
