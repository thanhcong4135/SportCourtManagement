type Props = {
  message: string;
  traceId?: string | null;
  onRetry?: () => void;
};

export function ErrorState({ message, traceId, onRetry }: Props) {
  return (
    <div className="ui-error-state">
      <p className="ui-error-state__title">{message}</p>
      {traceId ? <p className="ui-error-state__trace">Mã lỗi: {traceId}</p> : null}
      {onRetry ? (
        <button type="button" className="ui-button ui-button--secondary ui-button--sm" onClick={onRetry}>
          Thử lại
        </button>
      ) : null}
    </div>
  );
}

