export default function Footer() {
  return (
    <footer className="relative bg-night text-slate-soft">
      {/* Línea de marca superior */}
      <div className="h-px w-full bg-voice" aria-hidden="true" />

      <div className="mx-auto max-w-6xl px-6 py-14">
        <div className="flex flex-col gap-10 md:flex-row md:items-start md:justify-between">
          {/* Marca + tagline */}
          <div className="max-w-sm">
            <div className="inline-flex flex-col items-start gap-1">
              <span className="font-title text-2xl font-extrabold tracking-tight text-snow">
                SENDA
              </span>
              <span
                className="h-px w-full bg-gradient-to-r from-voice to-aura opacity-70"
                aria-hidden="true"
              />
            </div>
            <p className="mt-4 font-title text-lg font-semibold text-snow">
              Haz <span className="highlight-gradient-text">visible</span> la conversación.
            </p>
            <p className="mt-2 text-sm leading-relaxed text-slate-soft">
              Accesibilidad para personas sordas, con discapacidad auditiva y personas oyentes.
            </p>
          </div>

          {/* Datos del proyecto */}
          <div className="grid grid-cols-2 gap-x-12 gap-y-6 sm:grid-cols-3">
            <div>
              <h4 className="font-title text-xs font-bold uppercase tracking-[0.14em] text-aura">
                Evento
              </h4>
              <p className="mt-3 text-sm text-snow">Platanus Hack 2026</p>
              <p className="text-sm text-slate-soft">CDMX</p>
            </div>
            <div>
              <h4 className="font-title text-xs font-bold uppercase tracking-[0.14em] text-aura">
                Equipo
              </h4>
              <p className="mt-3 text-sm text-snow">Team 3</p>
              <p className="text-sm text-slate-soft">Track: New Interfaces</p>
            </div>
            <div>
              <h4 className="font-title text-xs font-bold uppercase tracking-[0.14em] text-aura">
                Producto
              </h4>
              <p className="mt-3 text-sm text-snow">SENDA</p>
              <p className="text-sm text-slate-soft">Subtítulos espaciales</p>
            </div>
          </div>
        </div>

        {/* Línea inferior */}
        <div className="mt-12 flex flex-col items-center justify-between gap-3 border-t border-line pt-6 text-sm text-slate-soft sm:flex-row">
          <span>© 2026 SENDA · Platanus Hack 2026 · Team 3</span>
          <span>Hecho con cariño y accesibilidad en mente.</span>
        </div>
      </div>
    </footer>
  );
}
