import { useEffect, type ReactNode } from "react";

type Props = {
  open: boolean;
  title: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
  children?: ReactNode;
};

export function Modal({
  open,
  title,
  message,
  confirmLabel = "Xác nhận",
  cancelLabel = "Hủy",
  onConfirm,
  onCancel,
  children,
}: Props) {
  useEffect(() => {
    if (!open) {
      return;
    }
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onCancel();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [open, onCancel]);

  if (!open) {
    return null;
  }

  return (
    <div className="ui-modal-root" role="presentation">
      <button type="button" className="ui-modal-backdrop" onClick={onCancel} aria-label="Đóng xác nhận" />
      <section className="ui-modal-panel" role="dialog" aria-modal="true" aria-label={title}>
        <h3>{title}</h3>
        {message ? <p className="ui-modal-message">{message}</p> : null}
        {children}
        <div className="ui-modal-actions">
          <button type="button" className="ui-button ui-button--ghost ui-button--sm" onClick={onCancel}>
            {cancelLabel}
          </button>
          <button type="button" className="ui-button ui-button--danger ui-button--sm" onClick={onConfirm}>
            {confirmLabel}
          </button>
        </div>
      </section>
    </div>
  );
}

