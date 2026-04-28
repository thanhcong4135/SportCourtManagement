import { useEffect, type ReactNode } from "react";

type Props = {
  open: boolean;
  title?: string;
  onClose: () => void;
  children: ReactNode;
};

export function Drawer({ open, title, onClose, children }: Props) {
  useEffect(() => {
    if (!open) {
      return;
    }
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [open, onClose]);

  if (!open) {
    return null;
  }

  return (
    <div className="ui-drawer-root" role="presentation">
      <button type="button" className="ui-drawer-backdrop" onClick={onClose} aria-label="Đóng bộ lọc" />
      <section className="ui-drawer-panel" role="dialog" aria-modal="true" aria-label={title || "Drawer"}>
        <header className="ui-drawer-header">
          <h3>{title ?? "Bảng điều khiển"}</h3>
          <button type="button" className="ui-button ui-button--ghost ui-button--sm" onClick={onClose}>
            Đóng
          </button>
        </header>
        <div className="ui-drawer-content">{children}</div>
      </section>
    </div>
  );
}

