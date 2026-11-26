import { Link, Outlet, useRouterState } from "@tanstack/react-router";
import clsx from "clsx";
import { Info, Moon, Search, Sun } from "lucide-react";
import { useEffect, useState } from "react";
import { Toaster } from "sonner";
import { NAV } from "@/app/nav";
import { useTheme } from "@/app/theme";
import { useHealth } from "@/api/queries";
import { istTime } from "@/lib/time";
import { Aura } from "./Aura";
import { CommandPalette } from "./CommandPalette";

export function Shell() {
  const [theme, toggleTheme] = useTheme();
  const [paletteOpen, setPaletteOpen] = useState(false);
  const path = useRouterState({ select: (s) => s.location.pathname });
  const current = NAV.find((n) => n.to === path) ?? (path === "/about" ? null : NAV[0]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setPaletteOpen((o) => !o);
      }
    };
    addEventListener("keydown", onKey);
    return () => {
      removeEventListener("keydown", onKey);
    };
  }, []);

  return (
    <>
      <Aura />
      <div className="relative z-[1] grid h-full grid-cols-[72px_minmax(0,1fr)] grid-rows-[56px_1fr]">
        <nav
          aria-label="Main"
          className="row-span-2 flex flex-col items-center gap-2 border-r border-line bg-s1 py-3.5 backdrop-blur-xl"
        >
          <Mark />
          {NAV.map((n) => (
            <Link
              key={n.to}
              to={n.to}
              title={n.live ? n.label : `${n.label} (designed, not built yet)`}
              aria-label={n.label}
              className="group relative grid size-11 place-items-center rounded-xl text-tx3 transition hover:bg-s2 hover:text-tx"
              activeOptions={{ exact: true, includeSearch: false }}
              activeProps={{
                className:
                  "!text-a bg-a/10 shadow-[0_0_0_1px_color-mix(in_srgb,var(--a)_35%,transparent),0_0_20px_color-mix(in_srgb,var(--a)_18%,transparent)]",
              }}
            >
              <n.icon size={18} strokeWidth={1.7} />
              {!n.live && <span className="absolute right-1.5 bottom-1.5 size-1.5 rounded-full bg-tx3" />}
            </Link>
          ))}
          <div className="flex-1" />
          <Link
            to="/about"
            aria-label="About and notices"
            className="grid size-11 place-items-center rounded-xl text-tx3 hover:bg-s2 hover:text-tx"
          >
            <Info size={18} strokeWidth={1.7} />
          </Link>
          <button
            type="button"
            onClick={toggleTheme}
            aria-label={theme === "dark" ? "Switch to light theme" : "Switch to dark theme"}
            className="grid size-11 cursor-pointer place-items-center rounded-xl text-tx3 hover:bg-s2 hover:text-tx"
          >
            {theme === "dark" ? <Moon size={18} strokeWidth={1.7} /> : <Sun size={18} strokeWidth={1.7} />}
          </button>
        </nav>

        <header className="flex items-center gap-3.5 border-b border-line bg-s1 px-5 backdrop-blur-xl">
          <h1 className="m-0 min-w-0 truncate text-[15px] font-semibold whitespace-nowrap">
            {current?.label ?? "About"}
            <small className="ml-1.5 font-medium text-tx3">
              {current?.subtitle ?? "licences and notices"}
            </small>
          </h1>
          <button
            type="button"
            onClick={() => {
              setPaletteOpen(true);
            }}
            className="ml-2 flex h-[34px] w-[min(400px,40vw)] shrink-0 cursor-pointer items-center gap-2 rounded-[10px] border border-line bg-s2 px-3 text-tx3"
          >
            <Search size={16} />
            <span className="truncate">Jump to, run, resolve…</span>
            <kbd className="num ml-auto rounded-[5px] border border-line2 px-1.5 text-[11px]">Ctrl K</kbd>
          </button>
          <div className="ml-auto flex items-center gap-2.5">
            <span className="pill pill-paper">Paper · Dev</span>
            <BackendPill />
            <Clock />
          </div>
        </header>

        <main className="relative overflow-auto px-5 pt-[18px] pb-6">
          <Outlet />
        </main>
      </div>
      <CommandPalette open={paletteOpen} onOpenChange={setPaletteOpen} />
      <Toaster theme={theme} position="bottom-right" toastOptions={{ className: "glass" }} />
    </>
  );
}

function Mark() {
  return (
    <div className="relative mb-4 size-[38px]" aria-hidden>
      <div className="spin absolute inset-0 rounded-full border-2 border-transparent border-t-a border-r-b" />
      <div
        className="spin absolute inset-[7px] rounded-full border-2 border-transparent border-t-c border-l-a"
        style={{ animationDirection: "reverse", animationDuration: "4s" }}
      />
      <div className="absolute inset-[15px] rounded-full bg-a shadow-[0_0_14px_var(--a)]" />
    </div>
  );
}

function BackendPill() {
  const h = useHealth();
  const up = h.data?.status === "UP";
  const label = h.isPending ? "Backend…" : up ? "Backend" : "Backend down";
  return (
    <span
      className="pill"
      title={
        up ? "Spring Boot is up" : "Start the backend: SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run"
      }
    >
      <span className={clsx("dot", up ? "dot-live" : h.isPending ? "bg-tx3" : "bg-dn")} />
      {label}
    </span>
  );
}

function Clock() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => {
      setNow(new Date());
    }, 1000);
    return () => {
      clearInterval(id);
    };
  }, []);
  return <span className="num text-xs text-tx2">{istTime(now)} IST</span>;
}
