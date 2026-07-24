import { Button, InputField, SelectField } from "../ui";

type Option = { label: string; value: string };

type VenueFilterProps = {
  keyword: string;
  sport: string;
  area: string;
  date: string;
  price: string;
  sort: string;
  sportOptions: Option[];
  areaOptions: Option[];
  priceOptions: Option[];
  sortOptions: Option[];
  onKeywordChange: (value: string) => void;
  onSportChange: (value: string) => void;
  onAreaChange: (value: string) => void;
  onDateChange: (value: string) => void;
  onPriceChange: (value: string) => void;
  onSortChange: (value: string) => void;
  onClear: () => void;
};

export function VenueFilter({
  keyword,
  sport,
  area,
  date,
  price,
  sort,
  sportOptions,
  areaOptions,
  priceOptions,
  sortOptions,
  onKeywordChange,
  onSportChange,
  onAreaChange,
  onDateChange,
  onPriceChange,
  onSortChange,
  onClear,
}: VenueFilterProps) {
  return (
    <section className="venue-filter">
      <div className="venue-filter-search">
        <InputField
          label="Tìm sân hoặc địa chỉ"
          placeholder="Tìm theo tên sân, khu vực hoặc địa chỉ..."
          value={keyword}
          onChange={(event) => onKeywordChange(event.target.value)}
        />
        <Button variant="ghost" className="venue-filter-clear" onClick={onClear}>
          Xóa bộ lọc
        </Button>
      </div>

      <div className="venue-filter-options">
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
          label="Ngày chơi"
          type="date"
          value={date}
          onChange={(event) => onDateChange(event.target.value)}
        />
        <SelectField
          label="Mức giá"
          options={priceOptions}
          value={price}
          onChange={(event) => onPriceChange(event.target.value)}
        />
        <SelectField
          label="Sắp xếp"
          options={sortOptions}
          value={sort}
          onChange={(event) => onSortChange(event.target.value)}
        />
      </div>
    </section>
  );
}
