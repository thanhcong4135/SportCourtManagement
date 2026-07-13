type PaymentMethodOption = {
  key: string;
  label: string;
  description: string;
  available?: boolean;
};

type PaymentMethodSelectorProps = {
  options: PaymentMethodOption[];
  value: string;
  onChange: (value: string) => void;
};

export function PaymentMethodSelector({ options, value, onChange }: PaymentMethodSelectorProps) {
  return (
    <div className="payment-method-selector-grid">
      {options.map((option) => {
        const disabled = option.available === false;
        return (
          <button
            key={option.key}
            type="button"
            className={`payment-method-choice${value === option.key ? " is-selected" : ""}${disabled ? " is-disabled" : ""}`}
            onClick={() => !disabled && onChange(option.key)}
            disabled={disabled}
          >
            <strong>{option.label}</strong>
            <span>{option.description}</span>
          </button>
        );
      })}
    </div>
  );
}

