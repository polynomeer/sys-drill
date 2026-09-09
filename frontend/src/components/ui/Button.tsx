import Link from "next/link";
import type { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "md" | "sm";

const VARIANT_CLASSES: Record<Variant, string> = {
  primary: "bg-accent text-accent-foreground hover:bg-accent/90",
  secondary: "border border-border bg-surface text-foreground hover:border-accent/50",
  ghost: "text-foreground-muted underline hover:text-foreground",
  danger: "text-danger underline hover:text-danger/80",
};

const SIZE_CLASSES: Record<Size, string> = {
  md: "px-4 py-2 text-sm",
  sm: "px-3 py-1 text-xs",
};

type CommonProps = {
  variant?: Variant;
  size?: Size;
  className?: string;
  children: React.ReactNode;
};

type LinkButtonProps = CommonProps & {
  href: string;
  target?: string;
};

type NativeButtonProps = CommonProps & ButtonHTMLAttributes<HTMLButtonElement>;

/**
 * SysDrill_UIUX_Design_Plan.docx §7 — Primary/Secondary/Ghost/Danger, one
 * strong Primary CTA per screen. `href` renders a styled next/link instead
 * of a button, since most call sites here are navigation ("시작", "리포트
 * 보기") not form submission.
 */
export function Button({
  variant = "primary",
  size = "md",
  className = "",
  children,
  href,
  target,
  ...rest
}: LinkButtonProps | (NativeButtonProps & { href?: undefined; target?: undefined })) {
  const base = "inline-flex items-center justify-center rounded-lg font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed";
  const classes = `${base} ${VARIANT_CLASSES[variant]} ${SIZE_CLASSES[size]} ${className}`;

  if (href !== undefined) {
    return (
      <Link href={href} target={target} className={classes}>
        {children}
      </Link>
    );
  }

  return (
    <button className={classes} {...(rest as ButtonHTMLAttributes<HTMLButtonElement>)}>
      {children}
    </button>
  );
}
