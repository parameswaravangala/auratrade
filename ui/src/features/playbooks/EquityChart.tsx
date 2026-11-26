import {
  createChart,
  LineSeries,
  PriceScaleMode,
  type IChartApi,
  type ISeriesApi,
  type LineData,
} from "lightweight-charts";
import { useEffect, useRef } from "react";
import type { EquityDay } from "@/api/schemas";
import { token } from "@/app/theme";
import { formatCompact } from "@/lib/format";

/**
 * Paper-account equity against the benchmark scaled to the same starting capital, both from Java.
 * Lightweight Charts (Apache-2.0) keeps its TradingView attribution logo on (ADR 0019).
 */
export function EquityChart({ days }: { days: EquityDay[] }) {
  const host = useRef<HTMLDivElement>(null);
  const chart = useRef<IChartApi | null>(null);
  const equity = useRef<ISeriesApi<"Line"> | null>(null);
  const bench = useRef<ISeriesApi<"Line"> | null>(null);

  useEffect(() => {
    if (!host.current) return;
    const c = createChart(host.current, {
      autoSize: true,
      layout: { attributionLogo: true },
      localization: { priceFormatter: (p: number) => `₹${formatCompact(p)}` },
    });
    bench.current = c.addSeries(LineSeries, {
      lineWidth: 1,
      priceLineVisible: false,
      lastValueVisible: false,
    });
    equity.current = c.addSeries(LineSeries, { lineWidth: 2, priceLineVisible: false });
    chart.current = c;
    const theme = () => {
      c.applyOptions({
        layout: {
          background: { color: "transparent" },
          textColor: token("--tx3"),
          fontFamily: "JetBrains Mono Variable",
          attributionLogo: true,
        },
        grid: { vertLines: { visible: false }, horzLines: { color: token("--grid") } },
        // Log scale: compounding over years reads as a slope, so early and late years compare fairly.
        rightPriceScale: { borderVisible: false, mode: PriceScaleMode.Logarithmic },
        timeScale: { borderVisible: false },
        crosshair: {
          vertLine: { color: token("--a"), labelBackgroundColor: token("--a") },
          horzLine: { color: token("--a"), labelBackgroundColor: token("--a") },
        },
      });
      equity.current?.applyOptions({ color: token("--a") });
      bench.current?.applyOptions({ color: token("--tx3") });
    };
    theme();
    const mo = new MutationObserver(theme);
    mo.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
    return () => {
      mo.disconnect();
      c.remove();
      chart.current = null;
    };
  }, []);

  useEffect(() => {
    equity.current?.setData(days.map((d): LineData => ({ time: d.date, value: d.equity })));
    bench.current?.setData(
      days.flatMap((d): LineData[] => (d.benchmark === null ? [] : [{ time: d.date, value: d.benchmark }])),
    );
    chart.current?.timeScale().fitContent();
  }, [days]);

  return <div ref={host} className="h-full w-full" data-testid="equity-chart" />;
}
