/** A single-choice chip row that can also be cleared (optional answers). */
export function PreferenceChips<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: { value: T; label: string; hint?: string }[];
  value: T | null;
  onChange: (value: T | null) => void;
}) {
  const hint = options.find((o) => o.value === value)?.hint;
  return (
    <fieldset className="flex flex-col gap-1.5 text-sm text-foreground-muted">
      <legend className="mb-1.5">{label}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((o) => (
          <button
            key={o.value}
            type="button"
            aria-pressed={value === o.value}
            onClick={() => onChange(value === o.value ? null : o.value)}
            className={`rounded-lg px-3 py-1.5 text-sm transition-colors ${
              value === o.value ? "bg-accent text-accent-foreground" : "border border-border text-foreground-muted hover:text-foreground"
            }`}
          >
            {o.label}
          </button>
        ))}
      </div>
      {hint && <p className="text-xs">{hint}</p>}
    </fieldset>
  );
}
