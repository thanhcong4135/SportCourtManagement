type Props = {
  count?: number;
};

export function SkeletonCard({ count = 3 }: Props) {
  return Array.from({ length: count }).map((_, index) => (
    <article className="discover-card discover-card--skeleton" key={`skeleton-${index}`}>
      <div className="discover-card-banner skeleton shimmer" />
      <div className="discover-card-body">
        <div className="skeleton-lines">
          <div className="skeleton shimmer skeleton-line-lg" />
          <div className="skeleton shimmer skeleton-line-md" />
          <div className="skeleton shimmer skeleton-line-sm" />
        </div>
        <div className="skeleton shimmer skeleton-pill" />
      </div>
    </article>
  ));
}

