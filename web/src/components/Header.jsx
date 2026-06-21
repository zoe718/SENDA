export default function Header() {
  return (
    <header className="absolute inset-x-0 top-0 z-50">
      <div className="mx-auto flex h-24 max-w-6xl items-start px-6 pt-5">
        <a
          href="#top"
          aria-label="SENDA — inicio"
          className="inline-flex rounded-2xl bg-black/35 p-1.5 shadow-[0_16px_40px_rgba(0,0,0,.38)] ring-1 ring-white/10 backdrop-blur-sm"
        >
          <img
            src="/resources/senda-logo.png"
            alt=""
            className="h-14 w-14 rounded-xl object-cover shadow-lg shadow-voice/20 sm:h-16 sm:w-16"
            aria-hidden="true"
          />
          <span className="sr-only">SENDA</span>
        </a>
      </div>
    </header>
  );
}
