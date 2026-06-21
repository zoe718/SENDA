import Reveal from "./Reveal.jsx";

const STEPS = [
  {
    n: 1,
    title: "Abre la cámara",
    desc: "Inicia SENDA desde tu celular y apunta a la conversación.",
    icon: (
      <svg viewBox="0 0 24 24" className="h-8 w-8 fill-none stroke-current stroke-2">
        <path d="M4 7h3l2-2h6l2 2h3v12H4z" />
        <circle cx="12" cy="13" r="3.5" />
      </svg>
    ),
  },
  {
    n: 2,
    title: "SENDA detecta quién habla",
    desc: "Une la voz activa con la persona que mueve los labios.",
    icon: (
      <svg viewBox="0 0 24 24" className="h-8 w-8 fill-none stroke-current stroke-2">
        <path d="M12 2a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z" className="fill-current stroke-none" />
        <path d="M19 11a7 7 0 0 1-14 0" />
      </svg>
    ),
  },
  {
    n: 3,
    title: "Resalta y subtitula",
    desc: "Ilumina al hablante y muestra el texto en tiempo real.",
    icon: (
      <svg viewBox="0 0 24 24" className="h-8 w-8 fill-none stroke-current stroke-2">
        <path d="M4 4h16v12H7l-3 3z" />
      </svg>
    ),
  },
];

export default function HowItWorks() {
  return (
    <section id="como-funciona" className="bg-mint py-24" aria-labelledby="como-title">
      <div className="mx-auto max-w-6xl px-6">
        <div className="mx-auto mb-14 max-w-2xl text-center">
          <span className="font-title text-xs font-bold uppercase tracking-[0.16em] text-[#0a9c74]">
            Cómo funciona
          </span>
          <h2
            id="como-title"
            className="mt-4 font-title text-3xl font-bold text-navy sm:text-4xl"
          >
            Tres pasos para hacerla visible.
          </h2>
        </div>

        <ol className="grid gap-7 md:grid-cols-3">
          {STEPS.map((s, i) => (
            <Reveal
              as="li"
              key={s.n}
              style={{ transitionDelay: `${i * 80}ms` }}
              className="relative rounded-[28px] border border-navy/8 bg-white p-9 pt-10 text-center shadow-sm transition-transform duration-300 hover:-translate-y-1.5 hover:shadow-xl hover:shadow-navy/5"
            >
              <span className="brand-gradient absolute -top-5 left-1/2 grid h-10 w-10 -translate-x-1/2 place-items-center rounded-full font-title font-extrabold text-[#06231b] shadow-lg shadow-aura/40">
                {s.n}
              </span>
              <div className="mx-auto mb-5 mt-3 grid h-16 w-16 place-items-center rounded-2xl bg-gradient-to-br from-aura/20 to-voice/20 text-[#0a9c74]">
                {s.icon}
              </div>
              <h3 className="font-title text-xl font-bold text-navy">{s.title}</h3>
              <p className="mt-2 text-slate-soft">{s.desc}</p>
            </Reveal>
          ))}
        </ol>
      </div>
    </section>
  );
}
