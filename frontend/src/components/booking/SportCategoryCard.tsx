import { Link } from "react-router-dom";
import type { CSSProperties } from "react";

type SportCategoryCardProps = {
  title: string;
  description: string;
  to: string;
  backgroundImage: string;
};

type SportCategoryCardStyle = CSSProperties & {
  "--sport-category-background": string;
};

export function SportCategoryCard({ title, description, to, backgroundImage }: SportCategoryCardProps) {
  const style: SportCategoryCardStyle = {
    "--sport-category-background": `url("${backgroundImage}")`,
  };

  return (
    <Link to={to} className="sport-category-card" style={style}>
      <strong>{title}</strong>
      <p>{description}</p>
      <span>Xem sân</span>
    </Link>
  );
}
