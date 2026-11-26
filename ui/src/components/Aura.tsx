import { useEffect, useRef } from "react";
import { token } from "@/app/theme";

const BLOBS: [string, number, number, number][] = [
  ["--a", 0.8, 0.1, 0.55],
  ["--b", 0.15, 0.9, 0.5],
  ["--c", 0.95, 0.85, 0.25],
];

/** Slow drifting light fields behind the app. Stops under prefers-reduced-motion. */
export function Aura() {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const cv = ref.current;
    const ctx = cv?.getContext("2d");
    if (!cv || !ctx) return;
    const fit = () => {
      cv.width = innerWidth;
      cv.height = innerHeight;
    };
    fit();
    addEventListener("resize", fit);
    const still = matchMedia("(prefers-reduced-motion: reduce)").matches;
    let raf = 0;
    const draw = (t: number) => {
      ctx.clearRect(0, 0, cv.width, cv.height);
      const light = document.documentElement.dataset.theme === "light";
      BLOBS.forEach(([v, x, y, s], i) => {
        const px = (x + Math.sin(t / 9000 + i * 2) * 0.08) * cv.width;
        const py = (y + Math.cos(t / 11000 + i) * 0.08) * cv.height;
        const r = Math.max(cv.width, cv.height) * s;
        const g = ctx.createRadialGradient(px, py, 0, px, py, r);
        g.addColorStop(0, token(v) + (light ? "1c" : "22"));
        g.addColorStop(1, "transparent");
        ctx.fillStyle = g;
        ctx.fillRect(0, 0, cv.width, cv.height);
      });
      if (!still) raf = requestAnimationFrame(draw);
    };
    raf = requestAnimationFrame(draw);
    return () => {
      cancelAnimationFrame(raf);
      removeEventListener("resize", fit);
    };
  }, []);
  return <canvas ref={ref} aria-hidden className="pointer-events-none fixed inset-0 z-0" />;
}
