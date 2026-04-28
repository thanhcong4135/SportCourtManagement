type Props = {
  title: string;
  description?: string;
};

export function EmptyState({ title, description }: Props) {
  return (
    <div className="ui-empty-state">
      <p className="ui-empty-state__title">{title}</p>
      {description ? <p className="ui-empty-state__desc">{description}</p> : null}
    </div>
  );
}

