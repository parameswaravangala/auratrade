import {
  CandlestickSeries,
  createChart,
  HistogramSeries,
  type CandlestickData,
  type HistogramData,
  type IChartApi,
  type ISeriesApi,
} from "lightweight-charts";
import { useEffect, useRef } from "react";
import { token } from "@/app/theme";
import type { ChartBar } from "@/lib/candles";

type Props = { bars: ChartBar[]; onHover?: (bar: ChartBar | null) => void };

/**
 * Daily candles with volume. Lightweight Charts (Apache-2.0) requires attribution: the chart's
 * TradingView logo stays on, and the About page carries the NOTICE text (ADR 0019).
 */
export function CandleChart({ bars, onHover }: Props) {
  const host = useRef<HTMLDivElement>(null);
  const chart = useRef<IChartApi | null>(null);
  const candles = useRef<ISeriesApi<"Candlestick"> | null>(null);
  const volume = useRef<ISeriesApi<"Histogram"> | null>(null);
  const hover = useRef(onHover);
  useEffect(() => {
    hover.current = onHover;
  });

  useEffect(() => {
    if (!host.current) return;
    const c = createChart(host.current, { autoSize: true, layout: { attributionLogo: true } });
    candles.current = c.addSeries(CandlestickSeries, { borderVisible: false, priceLineVisible: false });
    volume.current = c.addSeries(HistogramSeries, {
      priceScaleId: "",
      priceFormat: { type: "volume" },
      lastValueVisible: false,
      priceLineVisible: false,
    });
    volume.current.priceScale().applyOptions({ scaleMargins: { top: 0.84, bottom: 0 } });
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
        rightPriceScale: { borderVisible: false },
        timeScale: { borderVisible: false },
        crosshair: {
          vertLine: { color: token("--a"), labelBackgroundColor: token("--a") },
          horzLine: { color: token("--a"), labelBackgroundColor: token("--a") },
        },
      });
      candles.current?.applyOptions({
        upColor: token("--up"),
        downColor: token("--dn"),
        wickUpColor: token("--up"),
        wickDownColor: token("--dn"),
      });
    };
    theme();
    const mo = new MutationObserver(theme);
    mo.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
    c.subscribeCrosshairMove((p) => {
      const raw = candles.current ? p.seriesData.get(candles.current) : undefined;
      if (!raw || !("close" in raw) || typeof p.time !== "string") {
        hover.current?.(null);
        return;
      }
      const d = raw as CandlestickData;
      const v = volume.current ? p.seriesData.get(volume.current) : undefined;
      const vol = v && "value" in v ? (v as HistogramData).value : 0;
      hover.current?.({ time: p.time, open: d.open, high: d.high, low: d.low, close: d.close, volume: vol });
    });
    return () => {
      mo.disconnect();
      c.remove();
      chart.current = null;
    };
  }, []);

  useEffect(() => {
    const up = token("--up");
    const dn = token("--dn");
    candles.current?.setData(
      bars.map((b): CandlestickData => ({
        time: b.time,
        open: b.open,
        high: b.high,
        low: b.low,
        close: b.close,
      })),
    );
    volume.current?.setData(
      bars.map((b): HistogramData => ({
        time: b.time,
        value: b.volume,
        color: `${b.close >= b.open ? up : dn}55`,
      })),
    );
    chart.current?.timeScale().fitContent();
  }, [bars]);

  return <div ref={host} className="h-full min-h-[320px] w-full" data-testid="candle-chart" />;
}
