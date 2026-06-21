export default function Header() {
  return (
    <header className="absolute inset-x-0 top-0 z-50">
      <div className="mx-auto flex h-20 max-w-6xl items-center px-6">
        <a href="#top" aria-label="SENDA — inicio" className="flex items-center gap-3">
          <img
            src="/resources/senda-logo.png"
            alt=""
            className="h-11 w-11 rounded-lg border border-white/10 object-cover shadow-lg shadow-voice/20"
            aria-hidden="true"
          />
          <span
            className="font-title text-2xl font-extrabold tracking-tight text-snow"
            style={{ textShadow: "0 2px 14px rgba(0,0,0,0.35)" }}
          >
            SENDA
          </span>
        </a>
      </div>
    </header>
  );
}
