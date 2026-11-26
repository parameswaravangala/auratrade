import { ArrowRight } from "lucide-react";
import { useState } from "react";
import { useAsk } from "@/api/queries";
import { Panel } from "@/components/Panel";
import { ErrorState } from "@/components/States";
import { formatInt, formatUsd } from "@/lib/format";

type Turn = {
  id: string;
  q: string;
  a?: { text: string; model: string; tier: string; tokens: number; cost: number };
};

/**
 * Read-only questions through the LLM gateway (ADHOC_CHAT, local Ollama in development). The answer
 * is untrusted model output: it is shown as plain text, never as HTML, and can trigger nothing.
 */
export function Copilot({ symbol }: { symbol: string | undefined }) {
  const [draft, setDraft] = useState("");
  const [turns, setTurns] = useState<Turn[]>([]);
  const ask = useAsk();

  const submit = async () => {
    const q = draft.trim();
    if (!q || ask.isPending) return;
    setDraft("");
    const id = crypto.randomUUID();
    setTurns((t) => [...t, { id, q }]);
    const prompt = symbol ? `About the NSE share ${symbol}: ${q}` : q;
    // mutateAsync always settles; per-call onSuccess callbacks can be dropped when the observer
    // re-subscribes (seen with React StrictMode in development).
    try {
      const r = await ask.mutateAsync(prompt);
      const a = {
        text: r.text,
        model: r.model,
        tier: r.tier,
        tokens: r.usage.inputTokens + r.usage.outputTokens,
        cost: r.costUsd,
      };
      setTurns((t) => t.map((x) => (x.id === id ? { ...x, a } : x)));
    } catch {
      // ask.error is rendered below.
    }
  };

  return (
    <Panel
      title="Copilot"
      badge={<span className="tag tag-b">read-only</span>}
      aside="ADHOC_CHAT"
      className="min-h-0"
      delay={100}
    >
      <div className="flex min-h-0 flex-1 flex-col">
        <div className="grid flex-1 content-start gap-3 overflow-auto px-4 py-3">
          {turns.length === 0 && (
            <p className="m-0 text-xs text-tx3">
              Ask about {symbol ?? "a share"} in plain words. Answers come from the model and are not
              verified; numbers that matter are computed in Java.
            </p>
          )}
          {turns.map((t, i) => (
            <div key={t.id} className="grid gap-3">
              <div className="max-w-[85%] justify-self-end rounded-[14px] border border-line bg-s2 px-3.5 py-3">
                {t.q}
              </div>
              {t.a ? (
                <div className="rounded-[14px] border border-b/35 bg-[linear-gradient(160deg,color-mix(in_srgb,var(--b)_10%,transparent),transparent_70%)] px-3.5 py-3 leading-relaxed">
                  <div className="mb-2 flex flex-wrap gap-1.5">
                    <span className="tag tag-w">Model output · unverified</span>
                    <span className="tag">
                      {t.a.model} · {t.a.tier}
                    </span>
                    <span className="tag">
                      {formatInt(t.a.tokens)} tokens · {formatUsd(t.a.cost)}
                    </span>
                  </div>
                  <div className="whitespace-pre-wrap">{t.a.text}</div>
                </div>
              ) : (
                i === turns.length - 1 &&
                ask.isPending && (
                  <div className="shimmer h-16 rounded-[14px] border border-b/30" aria-label="Thinking" />
                )
              )}
            </div>
          ))}
          {ask.isError && <ErrorState error={ask.error} />}
        </div>
        <form
          className="mx-3 mb-3 flex items-center gap-2 rounded-[14px] border border-line2 bg-s2 py-2 pr-2 pl-3.5"
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
        >
          <input
            aria-label="Ask the copilot"
            value={draft}
            onChange={(e) => {
              setDraft(e.target.value);
            }}
            placeholder={`Ask about ${symbol ?? "a share"}…`}
            className="flex-1 border-0 bg-transparent text-tx outline-none placeholder:text-tx3"
          />
          <button
            type="submit"
            aria-label="Send"
            disabled={!draft.trim() || ask.isPending}
            className="btn btn-pri size-8 justify-center p-0"
          >
            <ArrowRight size={16} />
          </button>
        </form>
      </div>
    </Panel>
  );
}
