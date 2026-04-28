type Variant = "success" | "warning" | "danger" | "neutral";

type Props = {
  label: string;
  variant?: Variant;
};

export function StatusBadge({ label, variant = "neutral" }: Props) {
  return <span className={`ui-status-badge ui-status-badge--${variant}`}>{label}</span>;
}

