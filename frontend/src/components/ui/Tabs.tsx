type TabOption = {
  value: string;
  label: string;
};

type Props = {
  value: string;
  options: TabOption[];
  onChange: (next: string) => void;
  className?: string;
};

export function Tabs({ value, options, onChange, className }: Props) {
  const classes = ["ui-tabs", className ?? ""].filter(Boolean).join(" ");
  return (
    <div className={classes} role="tablist" aria-orientation="horizontal">
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="tab"
            aria-selected={active}
            className={`ui-tab ${active ? "is-active" : ""}`}
            onClick={() => onChange(option.value)}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

