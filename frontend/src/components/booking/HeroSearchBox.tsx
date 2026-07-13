import { Button, InputField, SelectField } from "../ui";

type Option = { label: string; value: string };

type HeroSearchBoxProps = {
  keyword: string;
  sport: string;
  area: string;
  date: string;
  onKeywordChange: (value: string) => void;
  onSportChange: (value: string) => void;
  onAreaChange: (value: string) => void;
  onDateChange: (value: string) => void;
  onSubmit: () => void;
  sportOptions: Option[];
  areaOptions: Option[];
  submitLabel?: string;
  onClose?: () => void;
  autoFocusKeyword?: boolean;
};

export function HeroSearchBox({
  keyword,
  sport,
  area,
  date,
  onKeywordChange,
  onSportChange,
  onAreaChange,
  onDateChange,
  onSubmit,
  sportOptions,
  areaOptions,
  submitLabel = "Tìm sân",
  onClose,
  autoFocusKeyword = false,
}: HeroSearchBoxProps) {
  return (
    <section id="hero-search-form" className="hero-search-box" aria-label="Tìm sân gần bạn">
      {onClose ? (
        <div className="hero-search-box-header">
          <strong></strong>
          <Button
            variant="ghost"
            size="sm"
            className="hero-search-close"
            aria-label="Đóng tìm kiếm"
            onClick={onClose}
          >
            Đóng tìm kiếm
          </Button>
        </div>
      ) : null}
      <InputField
        label="Tìm sân / khu vực"
        placeholder="Nhập tên sân hoặc khu vực"
        autoFocus={autoFocusKeyword}
        value={keyword}
        onChange={(event) => onKeywordChange(event.target.value)}
      />
      <SelectField
        label="Môn thể thao"
        options={sportOptions}
        value={sport}
        onChange={(event) => onSportChange(event.target.value)}
      />
      <SelectField
        label="Khu vực"
        options={areaOptions}
        value={area}
        onChange={(event) => onAreaChange(event.target.value)}
      />
      <InputField
        label="Ngày đặt"
        type="date"
        value={date}
        onChange={(event) => onDateChange(event.target.value)}
      />
      <Button variant="primary" size="lg" className="hero-search-submit" onClick={onSubmit}>
        {submitLabel}
      </Button>
    </section>
  );
}
