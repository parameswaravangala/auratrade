import clsx from "clsx";
import type { ReactNode } from "react";

type Props = {
  title: string;
  badge?: ReactNode;
  aside?: ReactNode;
  className?: string;
  children: ReactNode;
  delay?: number;
};

/** A glass card with the cockpit's uppercase header. */
export function Panel({ title, badge, aside, className, children, delay = 0 }: Props) {
  return (
    <section
      className={clsx("glass panel-in flex min-w-0 flex-col", className)}
      style={{ animationDelay: `${delay}ms` }}
      aria-label={title}
    >
      <header className="flex items-center gap-2 px-4 pt-3">
        <h2 className="m-0 text-[11px] font-semibold tracking-[.1em] text-tx2 uppercase">{title}</h2>
        {badge}
        {aside && <div className="ml-auto text-xs text-tx3">{aside}</div>}
      </header>
      {children}
    </section>
  );
}
