import Reveal from "./Reveal.jsx";

const FEATURES = [
  {
    title: "Identifica quién habla",
    desc: "Resalta con un aura a la persona activa en cada momento. Nunca pierdes de quién viene la voz.",
    icon: (
      <svg viewBox="0 0 24 24" className="h-7 w-7 fill-none stroke-current stroke-2">
        <circle cx="12" cy="8" r="4" />
        <path d="M5 21c0-4 3-6 7-6s7 2 7 6" />
      </svg>
    ),
    accent: true,
  },
  {
    title: "Ubica de dónde viene",
    desc: "Señales visuales indican la dirección de la voz para saber hacia dónde dirigir la atención.",
    icon: (
      <svg viewBox="0 0 24 24" className="h-7 w-7 fill-none stroke-current stroke-2">
        <path d="M12 2 4 7v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V7z" />
        <path d="m12 7-4 5h8z" className="fill-current stroke-none" />
      </svg>
    ),
  },
  {
    title: "Subtítulos en tiempo real",
    desc: "Reconocimiento de voz continuo en español, con baja latencia y funcionando sin internet.",
    icon: (
      <svg viewBox="0 0 24 24" className="h-7 w-7 fill-none stroke-current stroke-2">
        <path d="M4 4h16v12H7l-3 3z" />
        <path d="M8 9h8M8 12h5" />
      </svg>
    ),
  },
  {
    title: "El tono, hecho visible",
    desc: "El texto cambia de tamaño, peso y color según cómo se dijo: gritar, susurrar o preguntar.",
    icon: (
      <svg viewBox="0 0 24 24" className="h-7 w-7 fill-none stroke-current stroke-2" style={{ strokeLinecap: "round", strokeLinejoin: "round" }}>
        <path d="M3 12h3l2-6 4 14 3-9 2 5h4" />
      </svg>
    ),
  },
  {
    title: "Accesible de verdad",
    desc: "Nada depende solo del color: aura, etiqueta e ícono juntos. Alto contraste y movimiento sutil.",
    icon: (
      <svg viewBox="0 0 24 24" className="h-7 w-7 fill-none stroke-current stroke-2">
        <circle cx="12" cy="4" r="2" />
        <path d="M5 8h14M12 8v8M9 21l3-5 3 5" />
      </svg>
    ),
  },
  {
    title: "Privado y en tu celular",
    desc: "Procesa la voz en el dispositivo. Tus conversaciones no salen de tu teléfono.",
    icon: (
      <svg viewBox="0 0 24 24" className="h-7 w-7 fill-none stroke-current stroke-2">
        <path d="M6 10V8a6 6 0 0 1 12 0v2" />
        <rect x="4" y="10" width="16" height="10" rx="2" />
      </svg>
    ),
  },
];

export default function About() {
  return (
    <section id="about" className="bg-snow py-24" aria-labelledby="about-title">
      <div className="mx-auto max-w-6xl px-6">
        {/* Intro */}
        <Reveal className="mx-auto max-w-3xl text-center">
          <span className="font-title text-xs font-bold uppercase tracking-[0.16em] text-[#0a9c74]">
            Qué es SENDA
          </span>
          <h2
            id="about-title"
            className="mt-4 font-title text-3xl font-bold leading-tight text-navy sm:text-4xl"
          >
            No solo subtitulamos palabras.{" "}
            <span className="brand-gradient-text">Mostramos quién habla y dónde está.</span>
          </h2>
          <p className="mt-5 text-lg text-slate-soft">
            En una conversación grupal, perder el origen de una voz es perder el hilo. SENDA
            convierte la voz en una señal visual: detecta al hablante activo, lo ubica en el
            espacio y muestra subtítulos contextuales sobre él. Una capa digital clara y elegante
            sobre una conversación real — pensada para personas sordas, con discapacidad auditiva
            y personas oyentes.
          </p>
        </Reveal>

        {/* Grid de capacidades, bien distribuido */}
        <div className="mt-16 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((f, i) => (
            <Reveal
              as="article"
              key={f.title}
              style={{ transitionDelay: `${i * 60}ms` }}
              className={`group rounded-3xl border p-7 transition-transform duration-300 hover:-translate-y-1.5 ${
                f.accent
                  ? "border-aura/40 bg-gradient-to-br from-aura/15 to-voice/15 shadow-lg shadow-aura/10"
                  : "border-navy/8 bg-white shadow-sm"
              }`}
            >
              <div className="mb-5 grid h-14 w-14 place-items-center rounded-2xl bg-gradient-to-br from-aura/20 to-voice/20 text-[#0a9c74]">
                {f.icon}
              </div>
              <h3 className="font-title text-xl font-bold text-navy">{f.title}</h3>
              <p className="mt-2 text-slate-soft">{f.desc}</p>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
