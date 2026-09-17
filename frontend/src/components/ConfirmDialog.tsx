import { useEffect, useRef, type ReactNode } from 'react'
import { Button } from './ui'

type Props = {
  open: boolean
  title: string
  children: ReactNode
  confirmLabel: string
  loading?: boolean
  onConfirm: () => void
  onClose: () => void
}

// <dialog> bawaan: fokus terjebak di dalam dan kembali ke pemicu saat ditutup.
export function ConfirmDialog({ open, title, children, confirmLabel, loading, onConfirm, onClose }: Props) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      className="m-auto w-[min(92vw,420px)] rounded-2xl border border-border bg-card p-0 text-foreground shadow-lift backdrop:bg-foreground/30"
    >
      <div className="p-6">
        <h2 className="text-lg">{title}</h2>
        <div className="mt-2 text-sm text-muted-foreground">{children}</div>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} autoFocus>
            Batal
          </Button>
          <Button variant="danger" onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </dialog>
  )
}
