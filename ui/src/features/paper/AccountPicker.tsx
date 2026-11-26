import type { PaperAccount } from "@/api/schemas";
import { screenTitle } from "@/lib/playbook";

export function AccountPicker(props: {
  accounts: PaperAccount[];
  current: PaperAccount;
  onSelect: (id: number) => void;
}) {
  const { accounts, current, onSelect } = props;
  if (accounts.length < 2) {
    return (
      <span className="text-xs text-tx2">
        <span className="num text-tx">{current.name}</span> · {screenTitle(current.screen)}
      </span>
    );
  }
  return (
    <label className="flex items-center gap-2 text-[11px] text-tx3">
      Account
      <select
        className="rounded-lg border border-line2 bg-s2 px-2 py-1 text-xs text-tx"
        value={current.id}
        onChange={(e) => {
          onSelect(Number(e.target.value));
        }}
      >
        {accounts.map((a) => (
          <option key={a.id} value={a.id}>
            {a.name} · {screenTitle(a.screen)}
            {a.active ? "" : " (closed)"}
          </option>
        ))}
      </select>
    </label>
  );
}
