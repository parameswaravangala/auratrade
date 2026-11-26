import type { UseQueryResult } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { Empty, ErrorState } from "@/components/States";
import { formatDuration } from "@/lib/format";

type Props<T> = {
  /** null when nothing has been run for the current screen. */
  query: UseQueryResult<T> | null;
  idle: string;
  running: string;
  typical: string;
  children: (data: T) => ReactNode;
};

/**
 * The four states of a long, on-demand computation: not run, running (with elapsed time), failed,
 * done. A re-run keeps the previous result on screen, dimmed, until the new one arrives.
 */
export function RunState<T>({ query, idle, running, typical, children }: Props<T>) {
  const fetching = query?.isFetching ?? false;

  if (!query || (query.isPending && !fetching)) {
    return <p className="m-0 px-4 pt-2 pb-4 text-xs text-tx2">{idle}</p>;
  }
  if (query.isPending) {
    return (
      <div role="status" aria-label={running} className="grid gap-3 p-4">
        <div className="flex items-center gap-2 text-xs text-tx2">
          <span className="dot dot-live" />
          {running}… <Elapsed className="num text-tx" />
          <span className="text-tx3">({typical})</span>
        </div>
        <div className="shimmer h-40 rounded-xl border border-line" />
      </div>
    );
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  }
  if (query.data === undefined) return <Empty title="No result" />;
  return (
    <div className="relative">
      {fetching && (
        <div role="status" className="absolute top-2 right-4 z-10 text-xs text-tx2">
          <span className="dot dot-live" /> re-running… <Elapsed className="num" />
        </div>
      )}
      <div className={fetching ? "opacity-50 transition-opacity" : undefined}>{children(query.data)}</div>
    </div>
  );
}

/** Time since this element mounted, ticking once a second. */
function Elapsed({ className }: { className?: string }) {
  const [start] = useState(() => Date.now());
  const [now, setNow] = useState(start);
  useEffect(() => {
    const id = setInterval(() => {
      setNow(Date.now());
    }, 1000);
    return () => {
      clearInterval(id);
    };
  }, []);
  return <span className={className}>{formatDuration(now - start)}</span>;
}
