import { getRouteApi } from "@tanstack/react-router";
import { Play } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { JOBS, useJobRuns, useRunJob, useSeriesCounts, useSpend, type JobName } from "@/api/queries";
import { Panel } from "@/components/Panel";
import { Empty, ErrorState, Loading } from "@/components/States";
import { latestByJob } from "@/lib/timeline";
import { BudgetRing } from "./BudgetRing";
import { JobTimeline } from "./JobTimeline";
import { Narrative } from "./Narrative";
import { PipelineMap } from "./PipelineMap";
import { ResolverPanel } from "./ResolverPanel";
import { TodayStrip } from "./TodayStrip";
import { Universe } from "./Universe";

const route = getRouteApi("/");

export function CommandCenter() {
  const { q } = route.useSearch();
  const runs = useJobRuns();
  const spend = useSpend();
  const series = useSeriesCounts();
  const now = useNow(30_000);
  const latest = latestByJob(runs.data ?? []);

  return (
    <div className="grid gap-4 max-xl:grid-cols-1 xl:grid-cols-[1.55fr_1fr_1fr]" data-testid="command-center">
      <TodayStrip runs={runs.data} runsError={runs.error} now={now} />

      <Panel
        title="Data pipeline"
        badge={<span className="tag tag-a">live</span>}
        aside="coloured by each job's latest run"
        className="xl:col-span-2"
      >
        {runs.isError ? (
          <ErrorState error={runs.error} onRetry={() => void runs.refetch()} />
        ) : (
          <PipelineMap latest={latest} spend={spend.data} />
        )}
      </Panel>

      <Panel title="LLM budget · this month" delay={50}>
        {spend.isPending ? (
          <Loading />
        ) : spend.isError ? (
          <ErrorState error={spend.error} onRetry={() => void spend.refetch()} />
        ) : (
          <BudgetRing spend={spend.data} />
        )}
      </Panel>

      <Panel
        id="job-timeline"
        title="Job timeline · last 24 h (IST)"
        aside={<RunButtons />}
        className="xl:col-span-2"
        delay={100}
      >
        {runs.isPending ? (
          <Loading />
        ) : runs.isError ? (
          <ErrorState error={runs.error} onRetry={() => void runs.refetch()} />
        ) : (
          <JobTimeline runs={runs.data} now={now} known={JOBS} />
        )}
      </Panel>

      <Panel title="Name resolver" aside="how did it decide?" className="xl:row-span-2" delay={150}>
        <ResolverPanel key={q ?? ""} initial={q ?? ""} />
      </Panel>

      <Panel title="System narrative" aside="latest first" delay={200}>
        {runs.isPending ? (
          <Loading />
        ) : runs.isError ? (
          <ErrorState error={runs.error} />
        ) : runs.data.length === 0 ? (
          <Empty title="No job runs yet">Jobs run on schedule, at startup, or from Ctrl K.</Empty>
        ) : (
          <Narrative runs={runs.data} />
        )}
      </Panel>

      <Panel title="Instrument universe" aside="active by series" delay={250}>
        {series.isPending ? (
          <Loading />
        ) : series.isError ? (
          <ErrorState error={series.error} onRetry={() => void series.refetch()} />
        ) : series.data.length === 0 ? (
          <Empty title="No instruments loaded">Run instrument-master to load the NSE instrument file.</Empty>
        ) : (
          <Universe series={series.data} />
        )}
      </Panel>
    </div>
  );
}

function RunButtons() {
  const run = useRunJob();
  const go = (job: JobName) => {
    toast.promise(run.mutateAsync(job), {
      loading: `Running ${job}…`,
      success: (r) => `${r.job}: ${r.result.toLowerCase()}`,
      error: (e: unknown) => (e instanceof Error ? e.message : `${job} failed`),
    });
  };
  return (
    <span className="flex gap-1.5">
      {JOBS.map((j) => (
        <button
          key={j}
          type="button"
          className="tag tag-a flex cursor-pointer items-center gap-1 bg-transparent"
          onClick={() => {
            go(j);
          }}
          disabled={run.isPending}
        >
          <Play size={10} /> {j}
        </button>
      ))}
    </span>
  );
}

function useNow(everyMs: number): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => {
      setNow(new Date());
    }, everyMs);
    return () => {
      clearInterval(id);
    };
  }, [everyMs]);
  return now;
}
