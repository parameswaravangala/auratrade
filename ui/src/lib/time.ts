/** Times are stored in UTC by the backend; the UI shows them in IST (Asia/Kolkata), like CLAUDE.md asks. */
export const IST = "Asia/Kolkata";

const timeFmt = new Intl.DateTimeFormat("en-GB", {
  timeZone: IST,
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
});
const hmFmt = new Intl.DateTimeFormat("en-GB", {
  timeZone: IST,
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});
const dateFmt = new Intl.DateTimeFormat("en-CA", {
  timeZone: IST,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});
const dayMonthFmt = new Intl.DateTimeFormat("en-GB", { timeZone: IST, day: "2-digit", month: "short" });

/** "16:52:07" in IST. */
export const istTime = (d: Date): string => timeFmt.format(d);
/** "16:52" in IST. */
export const istHm = (d: Date): string => hmFmt.format(d);
/** The IST calendar date, "2026-11-26". */
export const istDate = (d: Date): string => dateFmt.format(d);
/** "26 Nov" in IST. */
export const istDayMonth = (d: Date): string => dayMonthFmt.format(d);

/** Adds whole days to a yyyy-MM-dd date (calendar arithmetic, no time zone involved). */
export function addDays(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const t = new Date(Date.UTC(y ?? 1970, (m ?? 1) - 1, d ?? 1));
  t.setUTCDate(t.getUTCDate() + days);
  return t.toISOString().slice(0, 10);
}

/** Whole calendar days from a to b (both yyyy-MM-dd). */
export function daysBetween(a: string, b: string): number {
  return Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 86_400_000);
}
