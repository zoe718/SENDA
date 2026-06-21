export default function Header() {
  return (
    <header className="absolute inset-x-0 top-0 z-50">
      <div className="mx-auto flex h-20 max-w-6xl items-center px-6">
        <a
          href="#top"
          aria-label="SENDA — inicio"
          className="group inline-flex flex-col items-start gap-1"
        >
          <span
            className="font-title text-2xl font-extrabold tracking-tight text-snow sm:text-[1.7rem]"
            style={{ textShadow: "0 2px 16px rgba(0,0,0,0.38)" }}
          >
            SENDA
          </span>
          <span
            className="h-px w-full origin-left scale-x-75 bg-gradient-to-r from-voice to-aura opacity-70 transition-transform duration-300 group-hover:scale-x-100"
            aria-hidden="true"
          />
        </a>
      </div>
    </header>
  );
}
