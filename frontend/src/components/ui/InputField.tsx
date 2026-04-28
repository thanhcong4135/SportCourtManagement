import type { InputHTMLAttributes } from "react";

type Props = InputHTMLAttributes<HTMLInputElement> & {
  label?: string;
  error?: string;
};

export function InputField({ label, error, className, id, ...rest }: Props) {
  const inputId = id ?? rest.name;
  const classes = ["ui-input", error ? "ui-input--error" : "", className ?? ""]
    .filter(Boolean)
    .join(" ");

  return (
    <label className="ui-field" htmlFor={inputId}>
      {label ? <span className="ui-field__label">{label}</span> : null}
      <input id={inputId} className={classes} {...rest} />
      {error ? <span className="ui-field__error">{error}</span> : null}
    </label>
  );
}

