import type { Candle } from "@/api/schemas";
import { addDays, daysBetween, istDate } from "./time";

export type ChartBar = {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
};

/** DAY bars are stamped at IST midnight (ADR 0013); the chart's time is that IST trading date. */
export function toChartBars(candles: Candle[]): ChartBar[] {
  return candles
    .map((c) => ({
      time: istDate(new Date(c.ts)),
      open: c.open,
      high: c.high,
      low: c.low,
      close: c.close,
      volume: c.volume,
    }))
    .sort((a, b) => a.time.localeCompare(b.time));
}

export type Summary = { count: number; first: string; last: string; close: number; changePct: number | null };

/** For display only: the last close and its change from the previous bar. */
export function summarise(bars: ChartBar[]): Summary | null {
  const last = bars.at(-1);
  const first = bars[0];
  if (!last || !first) return null;
  const prev = bars.at(-2);
  return {
    count: bars.length,
    first: first.time,
    last: last.time,
    close: last.close,
    changePct: prev ? ((last.close - prev.close) / prev.close) * 100 : null,
  };
}

/**
 * Calendar days between the newest bar and today in IST. Weekends and holidays are not discounted:
 * the trading calendar lives in the backend, and `/api/v1` should return freshness (ADR 0019).
 */
export function calendarDaysBehind(lastBar: string, now: Date): number {
  return Math.max(0, daysBetween(lastBar, istDate(now)));
}

export const RANGES = { "1M": 30, "3M": 91, "6M": 182, "1Y": 365, "3Y": 1095 } as const;
export type RangeKey = keyof typeof RANGES;

export function rangeDates(range: RangeKey, now: Date): { from: string; to: string } {
  const to = istDate(now);
  return { from: addDays(to, -RANGES[range]), to };
}
