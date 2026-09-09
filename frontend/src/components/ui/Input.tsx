import type { InputHTMLAttributes, TextareaHTMLAttributes } from "react";

const FIELD_CLASSES =
  "rounded-lg border border-border bg-surface px-3 py-2 text-foreground placeholder:text-foreground-muted focus:border-accent focus:outline-none";

type BaseProps = {
  label?: string;
  className?: string;
};

export function Input({ label, className = "", ...rest }: BaseProps & InputHTMLAttributes<HTMLInputElement>) {
  const input = <input className={`${FIELD_CLASSES} ${className}`} {...rest} />;
  if (!label) return input;
  return (
    <label className="flex flex-col gap-1 text-sm text-foreground-muted">
      {label}
      {input}
    </label>
  );
}

/** Same field styling, for the multi-line answer/prompt boxes. */
export function Textarea({ label, className = "", ...rest }: BaseProps & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const textarea = <textarea className={`${FIELD_CLASSES} ${className}`} {...rest} />;
  if (!label) return textarea;
  return (
    <label className="flex flex-col gap-1 text-sm text-foreground-muted">
      {label}
      {textarea}
    </label>
  );
}
