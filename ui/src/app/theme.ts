import { useCallback, useEffect, useState } from "react";

export type Theme = "dark" | "light";
const KEY = "auratrade.theme";

function stored(): Theme {
  try {
    return localStorage.getItem(KEY) === "light" ? "light" : "dark";
  } catch {
    return "dark";
  }
}

/** Dark first; the choice is a per-browser convenience, so storage failures are ignored. */
export function useTheme(): [Theme, () => void] {
  const [theme, setTheme] = useState<Theme>(stored);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem(KEY, theme);
    } catch {
      /* private mode: keep the in-memory choice */
    }
  }, [theme]);
  const toggle = useCallback(() => {
    setTheme((t) => (t === "dark" ? "light" : "dark"));
  }, []);
  return [theme, toggle];
}

/** Reads a design token, for canvas and chart code that cannot use CSS variables directly. */
export function token(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}
