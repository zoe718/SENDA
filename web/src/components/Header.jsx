/**
 * Header minimalista: solo la marca SENDA.
 * Cuando tengas el logo, reemplaza el <span className="brand-mark"> por:
 *   <img src="/resources/tu-logo.svg" alt="SENDA" className="h-9 w-auto" />
 * (coloca el archivo en web/public/resources/).
 */
export default function Header() {
  return (
    <header className="absolute inset-x-0 top-0 z-50">
      <div className="mx-auto flex h-20 max-w-6xl items-center px-6">
        <a href="#top" aria-label="SENDA — inicio" className="flex items-center gap-3">
          <span
            className="brand-gradient grid h-10 w-10 place-items-center rounded-lg shadow-lg shadow-voice/20"
            aria-hidden="true"
          >
            <span className="h-3 w-3 rounded-full bg-snow shadow-[0_0_0_3px_rgba(255,255,255,0.28)]" />
          </span>
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
