export default function AppMockupCard({ title, children }) {
  return (
    <div className="w-full max-w-[21rem] overflow-hidden rounded-2xl border border-line bg-panel shadow-[0_28px_90px_rgba(0,0,0,.45)]">
      <div className="flex h-12 items-center justify-between border-b border-line px-4">
        <div className="flex items-center gap-1.5" aria-hidden="true">
          <span className="h-2.5 w-2.5 rounded-full bg-[#ff6b6b]" />
          <span className="h-2.5 w-2.5 rounded-full bg-[#f7c948]" />
          <span className="h-2.5 w-2.5 rounded-full bg-[#7C9A88]" />
        </div>
        <span className="font-title text-xs font-semibold uppercase tracking-[0.14em] text-slate-soft">
          {title}
        </span>
      </div>
      <div className="min-h-[26rem] bg-night p-4 sm:p-5">{children}</div>
    </div>
  );
}
