import { Button } from "../ui";

type BookingSummaryProps = {
  title: string;
  subtitle: string;
  duration: string;
  note: string;
  ctaLabel: string;
  onNext: () => void;
  disabled?: boolean;
};

export function BookingSummary({
  title,
  subtitle,
  duration,
  note,
  ctaLabel,
  onNext,
  disabled,
}: BookingSummaryProps) {
  return (
    <section className="booking-summary-card">
      <div className="booking-summary-content">
        <div>
          <p>Đã chọn</p>
          <strong>{title}</strong>
          <small>{subtitle}</small>
        </div>
        <div>
          <p>Tổng thời lượng</p>
          <strong>{duration}</strong>
          <small>{note}</small>
        </div>
      </div>
      <Button variant="primary" onClick={onNext} disabled={disabled}>
        {ctaLabel}
      </Button>
    </section>
  );
}

