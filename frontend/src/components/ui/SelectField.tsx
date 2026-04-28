import type { SelectHTMLAttributes } from "react";

export type SelectOption = {
  label: string;
  value: string;
};

type Props = SelectHTMLAttributes<HTMLSelectElement> & {
  label?: string;
  options: SelectOption[];
  error?: string;
};

export function SelectField({ label, options, error, className, id, ...rest }: Props) {
  const selectId = id ?? rest.name;
  const classes = ["ui-select", error ? "ui-select--error" : "", className ?? ""]
    .filter(Boolean)
    .join(" ");

  return (
    <label className="ui-field" htmlFor={selectId}>
      {label ? <span className="ui-field__label">{label}</span> : null}
      <select id={selectId} className={classes} {...rest}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error ? <span className="ui-field__error">{error}</span> : null}
    </label>
  );
}

