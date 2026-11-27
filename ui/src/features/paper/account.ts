import { useEffect, useState } from "react";
import type { PaperAccount } from "@/api/schemas";

/** The account a paper screen shows: the one in the URL, else the first active one. */
export function pickAccount(accounts: PaperAccount[] | undefined, wanted: number | undefined) {
  if (!accounts) return undefined;
  return accounts.find((a) => a.id === wanted) ?? accounts.find((a) => a.active) ?? accounts[0];
}

/** The current time, ticking every `ms`, for countdowns. */
export function useNow(ms = 30_000): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => {
      setNow(new Date());
    }, ms);
    return () => {
      clearInterval(id);
    };
  }, [ms]);
  return now;
}
