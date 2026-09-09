export function EmptyState({ message, action, className = "" }: { message: string; action?: React.ReactNode; className?: string }) {
  return (
    <div className={`flex flex-col items-start gap-2 text-sm text-foreground-muted ${className}`}>
      <p>{message}</p>
      {action}
    </div>
  );
}
