import type { ReactNode } from "react";

export function Loading({ label = "Loading" }: { label?: string }) {
  return (
    <div role="status" className="flex flex-col gap-2 p-4" aria-label={label}>
      {[80, 60, 70].map((w) => (
        <div key={w} className="shimmer h-3 rounded bg-line2" style={{ width: `${w}%` }} />
      ))}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const msg = error instanceof Error ? error.message : "Something went wrong";
  return (
    <div role="alert" className="m-4 rounded-xl border border-dn/40 bg-dn/5 p-3 text-xs text-tx2">
      <div className="mb-1 font-semibold text-dn">Could not load</div>
      <div>{msg}</div>
      {onRetry && (
        <button type="button" className="btn mt-3" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="m-4 grid place-items-center gap-2 rounded-xl border border-dashed border-line2 p-6 text-center text-tx2">
      <div className="font-semibold text-tx">{title}</div>
      {children}
    </div>
  );
}
