import { useNavigate } from "@tanstack/react-router";
import { Command } from "cmdk";
import { LineChart, Play, Search } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { NAV } from "@/app/nav";
import { JOBS, useRunJob, type JobName } from "@/api/queries";

type Props = { open: boolean; onOpenChange: (open: boolean) => void };

const itemClass =
  "flex cursor-pointer items-center gap-2.5 rounded-[10px] px-2.5 py-2 text-tx2 data-[selected=true]:bg-a/10 data-[selected=true]:text-tx data-[selected=true]:shadow-[inset_2px_0_0_var(--a)]";
const groupClass =
  "px-2 pt-2 [&_[cmdk-group-heading]]:mx-2.5 [&_[cmdk-group-heading]]:my-1.5 [&_[cmdk-group-heading]]:text-[10.5px] [&_[cmdk-group-heading]]:tracking-[.1em] [&_[cmdk-group-heading]]:text-tx3 [&_[cmdk-group-heading]]:uppercase";

/** Ctrl K: jump to a screen, run a dev job, open a dossier or resolve a name. */
export function CommandPalette({ open, onOpenChange }: Props) {
  const [q, setQ] = useState("");
  const navigate = useNavigate();
  const runJob = useRunJob();
  const close = () => {
    onOpenChange(false);
    setQ("");
  };
  const term = q.trim();

  const run = (job: JobName) => {
    close();
    toast.promise(runJob.mutateAsync(job), {
      loading: `Running ${job}…`,
      success: (r) => `${r.job}: ${r.result.toLowerCase()}`,
      error: (e: unknown) => (e instanceof Error ? e.message : `${job} failed`),
    });
  };

  return (
    <Command.Dialog
      open={open}
      onOpenChange={onOpenChange}
      label="Command palette"
      shouldFilter
      overlayClassName="fixed inset-0 z-50 bg-[rgba(2,4,10,.55)] backdrop-blur-sm"
      contentClassName="fixed top-[12vh] left-1/2 z-50 w-[min(620px,92vw)] -translate-x-1/2 overflow-hidden rounded-[18px] border border-line2 bg-s1 shadow-[var(--shadow)] backdrop-blur-2xl"
    >
      <div className="flex items-center gap-2.5 border-b border-line px-[18px] py-4">
        <Search size={18} className="text-tx3" />
        <Command.Input
          value={q}
          onValueChange={setQ}
          placeholder="Type a screen, a job, a symbol or a company name"
          className="flex-1 border-0 bg-transparent text-base text-tx outline-none placeholder:text-tx3"
        />
        <span className="tag">Esc</span>
      </div>
      <Command.List className="max-h-[60vh] overflow-auto pb-2">
        {/* With a term the look-up items always show, so "nothing matches" would be wrong. */}
        {!term && <Command.Empty className="p-4 text-tx3">Nothing matches.</Command.Empty>}
        <Command.Group heading="Go to" className={groupClass}>
          {NAV.map((n) => (
            <Command.Item
              key={n.to}
              value={`go ${n.label}`}
              className={itemClass}
              onSelect={() => {
                close();
                void navigate({ to: n.to });
              }}
            >
              <n.icon size={16} /> {n.label}
              {!n.live && <span className="tag ml-auto">designed</span>}
            </Command.Item>
          ))}
        </Command.Group>
        <Command.Group heading="Run (dev)" className={groupClass}>
          {JOBS.map((j) => (
            <Command.Item
              key={j}
              value={`run ${j}`}
              className={itemClass}
              onSelect={() => {
                run(j);
              }}
            >
              <Play size={16} /> Run {j} now<span className="tag ml-auto">dev</span>
            </Command.Item>
          ))}
        </Command.Group>
        {term && (
          <Command.Group heading="Look up" forceMount className={groupClass}>
            <Command.Item
              value="lookup-dossier"
              forceMount
              className={itemClass}
              onSelect={() => {
                close();
                void navigate({ to: "/dossier", search: { symbol: term.toUpperCase() } });
              }}
            >
              <LineChart size={16} /> Open dossier for{" "}
              <span className="num text-tx">{term.toUpperCase()}</span>
            </Command.Item>
            <Command.Item
              value="lookup-resolve"
              forceMount
              className={itemClass}
              onSelect={() => {
                close();
                void navigate({ to: "/", search: { q: term } });
              }}
            >
              <Search size={16} /> Resolve “{term}”
            </Command.Item>
          </Command.Group>
        )}
      </Command.List>
    </Command.Dialog>
  );
}
