import { forwardRef, useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react'
import { initial } from '../lib/format'
import { noteColor, NOTE_BG } from '../lib/noteColor'

export function cx(...classes: Array<string | false | null | undefined>) {
  return classes.filter(Boolean).join(' ')
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost'
  size?: 'md' | 'sm'
  loading?: boolean
}

const VARIANTS = {
  primary: 'bg-primary text-primary-foreground shadow-lift hover:bg-primary-hover',
  secondary: 'bg-card text-foreground border border-border hover:bg-secondary',
  danger: 'bg-card text-danger-text border border-[#F1C2C5] hover:bg-danger-soft',
  ghost: 'text-foreground hover:bg-secondary',
}

export function Button({ variant = 'primary', size = 'md', loading, disabled, className, children, ...rest }: ButtonProps) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={cx(
        'inline-flex items-center justify-center gap-2 rounded-lg font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-55',
        size === 'md' ? 'px-4 py-2.5 text-sm' : 'px-3 py-1.5 text-[13px]',
        VARIANTS[variant],
        className,
      )}
    >
      {loading && <span className="size-3.5 animate-spin rounded-full border-2 border-current border-t-transparent" aria-hidden />}
      {children}
    </button>
  )
}

type FieldShellProps = {
  label: string
  error?: string
  hint?: ReactNode
  count?: { value: number; max: number }
  children: (id: string, describedBy: string | undefined) => ReactNode
}

// Penghitung karakter muncul hanya saat ≥ 80% batas (docs/DESIGN-HANDOVER.md §5)
export function FieldShell({ label, error, hint, count, children }: FieldShellProps) {
  const id = useId()
  const messageId = `${id}-msg`
  const showCount = count && count.value >= count.max * 0.8
  const hasMessage = Boolean(error || hint)
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-[13px] font-semibold">
        {label}
      </label>
      {children(id, hasMessage ? messageId : undefined)}
      {(hasMessage || showCount) && (
        <div className="flex items-start justify-between gap-3 text-[12.5px]">
          <span id={messageId} className={error ? 'text-danger-text' : 'text-muted-foreground'} role={error ? 'alert' : undefined}>
            {error ?? hint}
          </span>
          {showCount && (
            <span className={cx('shrink-0 tabular-nums', count.value > count.max ? 'text-danger-text' : 'text-muted-foreground')}>
              {count.value.toLocaleString('id-ID')} / {count.max.toLocaleString('id-ID')}
            </span>
          )}
        </div>
      )}
    </div>
  )
}

const inputClass = (invalid: boolean) =>
  cx(
    'w-full rounded-lg border bg-card px-3 py-2.5 text-[15px] text-foreground placeholder:text-muted-foreground/80 focus:border-primary focus:outline-none focus:ring-3 focus:ring-primary/30',
    invalid ? 'border-danger' : 'border-border',
  )

type InputFieldProps = InputHTMLAttributes<HTMLInputElement> & Omit<FieldShellProps, 'children'>

export const InputField = forwardRef<HTMLInputElement, InputFieldProps>(function InputField(
  { label, error, hint, count, className, ...rest },
  ref,
) {
  return (
    <FieldShell label={label} error={error} hint={hint} count={count}>
      {(id, describedBy) => (
        <input ref={ref} id={id} aria-invalid={Boolean(error)} aria-describedby={describedBy} className={cx(inputClass(Boolean(error)), className)} {...rest} />
      )}
    </FieldShell>
  )
})

type TextareaFieldProps = TextareaHTMLAttributes<HTMLTextAreaElement> & Omit<FieldShellProps, 'children'>

export const TextareaField = forwardRef<HTMLTextAreaElement, TextareaFieldProps>(function TextareaField(
  { label, error, hint, count, className, ...rest },
  ref,
) {
  return (
    <FieldShell label={label} error={error} hint={hint} count={count}>
      {(id, describedBy) => (
        <textarea ref={ref} id={id} aria-invalid={Boolean(error)} aria-describedby={describedBy} className={cx(inputClass(Boolean(error)), 'leading-relaxed', className)} {...rest} />
      )}
    </FieldShell>
  )
})

export function Tags({ tags, onWhite }: { tags: string[]; onWhite?: boolean }) {
  if (!tags.length) return null
  return (
    <ul className="flex flex-wrap gap-1" aria-label="Tag">
      {tags.map((tag) => (
        <li key={tag} className={cx('rounded-full px-2 py-px text-[11.5px] font-medium text-foreground', onWhite ? 'bg-secondary' : 'bg-white/60')}>
          {tag}
        </li>
      ))}
    </ul>
  )
}

export function Avatar({ name, seed, size = 34 }: { name: string; seed: string; size?: number }) {
  return (
    <span
      aria-hidden
      className={cx('inline-grid shrink-0 place-items-center rounded-full font-semibold text-foreground', NOTE_BG[noteColor(seed)])}
      style={{ width: size, height: size, fontSize: size * 0.4 }}
    >
      {initial(name)}
    </span>
  )
}

export function OwnerChip({ name }: { name: string }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-white/85 px-2.5 py-0.5 text-xs font-semibold text-foreground">
      <ShareIcon className="size-3" />
      dibagikan oleh {name} · hanya baca
    </span>
  )
}

export function StateCard({ icon, title, children, action, tone = 'lilac' }: {
  icon: ReactNode
  title: string
  children?: ReactNode
  action?: ReactNode
  tone?: 'lilac' | 'mint' | 'pink'
}) {
  const bg = { lilac: 'bg-note-lilac', mint: 'bg-note-mint', pink: 'bg-note-pink' }[tone]
  return (
    <div className="mx-auto flex max-w-md flex-col items-center rounded-2xl border border-border bg-card px-8 py-10 text-center shadow-soft">
      <span className={cx('mb-4 grid size-11 place-items-center rounded-lg text-foreground', bg)} aria-hidden>
        {icon}
      </span>
      <h2 className="text-lg">{title}</h2>
      {children && <p className="mt-1.5 text-sm text-muted-foreground">{children}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function CardSkeleton() {
  return (
    <div className="rounded-xl border border-border bg-card p-5" aria-hidden>
      <div className="h-4 w-2/3 animate-pulse rounded-md bg-secondary" />
      <div className="mt-4 h-3 animate-pulse rounded-md bg-secondary" />
      <div className="mt-2 h-3 w-5/6 animate-pulse rounded-md bg-secondary" />
      <div className="mt-6 h-3 w-1/3 animate-pulse rounded-md bg-secondary" />
    </div>
  )
}

// ===== ikon (inline SVG, stroke mengikuti currentColor) =====
type IconProps = { className?: string }
const iconBase = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round', viewBox: '0 0 24 24' } as const

export const PenIcon = ({ className = 'size-5' }: IconProps) => (
  <svg {...iconBase} className={className} aria-hidden><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" /></svg>
)
export const ShareIcon = ({ className = 'size-5' }: IconProps) => (
  <svg {...iconBase} className={className} aria-hidden><path d="M7 17 17 7" /><path d="M8 7h9v9" /></svg>
)
export const SearchIcon = ({ className = 'size-5' }: IconProps) => (
  <svg {...iconBase} className={className} aria-hidden><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
)
export const AlertIcon = ({ className = 'size-5' }: IconProps) => (
  <svg {...iconBase} className={className} aria-hidden><path d="M12 9v4M12 17h.01" /><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" /></svg>
)
export const LockIcon = ({ className = 'size-5' }: IconProps) => (
  <svg {...iconBase} className={className} aria-hidden><rect x="4" y="11" width="16" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg>
)
export const BackIcon = ({ className = 'size-4' }: IconProps) => (
  <svg {...iconBase} className={className} aria-hidden><path d="m15 18-6-6 6-6" /></svg>
)
export const CloseIcon = ({ className = 'size-4' }: IconProps) => (
  <svg {...iconBase} className={className} aria-hidden><path d="M18 6 6 18M6 6l12 12" /></svg>
)
export const PlusIcon = ({ className = 'size-4' }: IconProps) => (
  <svg {...iconBase} className={className} aria-hidden><path d="M12 5v14M5 12h14" /></svg>
)
