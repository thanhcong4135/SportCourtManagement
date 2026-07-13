import { Button } from "../ui";

type MobileBookingBarProps = {
  label: string;
  value: string;
  ctaLabel: string;
  onClick: () => void;
  disabled?: boolean;
};

export function MobileBookingBar({ label, value, ctaLabel, onClick, disabled }: MobileBookingBarProps) {
  return (
    <div className="mobile-booking-bar">
      <div>
        <p>{label}</p>
        <strong>{value}</strong>
      </div>
      <Button variant="primary" onClick={onClick} disabled={disabled}>
        {ctaLabel}
      </Button>
    </div>
  );
}

