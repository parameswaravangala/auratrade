import {
  Activity,
  BriefcaseBusiness,
  CalendarRange,
  CircleCheckBig,
  Cpu,
  FileText,
  LineChart,
  Sparkles,
  type LucideIcon,
} from "lucide-react";

export type NavItem = {
  to: "/" | "/dossier" | "/research" | "/playbooks" | "/approvals" | "/portfolio" | "/agents" | "/calendar";
  label: string;
  subtitle: string;
  icon: LucideIcon;
  live: boolean;
};

/** Every screen in the cockpit (docs/ui/mockup.html). `live` screens use real backend data today. */
export const NAV: NavItem[] = [
  { to: "/", label: "Command center", subtitle: "systems, data, budget", icon: Activity, live: true },
  {
    to: "/dossier",
    label: "Instrument dossier",
    subtitle: "chart, provenance, copilot",
    icon: LineChart,
    live: true,
  },
  { to: "/research", label: "Research desk", subtitle: "news, themes, theses", icon: Sparkles, live: false },
  { to: "/playbooks", label: "Playbook lab", subtitle: "rules and backtests", icon: FileText, live: false },
  {
    to: "/approvals",
    label: "Approval queue",
    subtitle: "you decide every paper order",
    icon: CircleCheckBig,
    live: false,
  },
  {
    to: "/portfolio",
    label: "Paper portfolio",
    subtitle: "positions and journal",
    icon: BriefcaseBusiness,
    live: false,
  },
  {
    to: "/agents",
    label: "Agent observatory",
    subtitle: "tiers, tokens, traces, evals",
    icon: Cpu,
    live: false,
  },
  {
    to: "/calendar",
    label: "Data calendar",
    subtitle: "coverage and validation",
    icon: CalendarRange,
    live: false,
  },
];
