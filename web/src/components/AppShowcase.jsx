import AppMockupCard from "./AppMockupCard.jsx";
import Reveal from "./Reveal.jsx";

const showcaseCopy = {
  conversation: {
    eyebrow: "CONVERSACIÓN EN VIVO",
    title: "Cada voz, su lugar.",
    description:
      "SENDA separa el hilo por hablante para que la transcripción no sea una pared de texto. Cada intervención conserva contexto, origen y ritmo.",
  },
  camera: {
    eyebrow: "ESPACIO",
    title: "Sabes quién habla, sin mirar dos veces.",
    description:
      "La cámara resalta a la persona activa con una señal visual clara y subtítulos anclados a su posición en la escena.",
  },
  voice: {
    eyebrow: "VÍA DE REGRESO",
    title: "Escribe, y que te escuchen.",
    description:
      "Cuando responder con voz no es posible o no es cómodo, SENDA convierte texto en una salida audible para volver a entrar en la conversación.",
  },
};

function ChatBubble({ side = "left", label, color, children, muted = false }) {
  const align = side === "right" ? "ml-auto border-r-2 text-right" : "mr-auto border-l-2";

  return (
    <div
      className={`max-w-[86%] rounded-2xl bg-panel-soft px-4 py-3 shadow-sm shadow-black/20 ${align}`}
      style={{ borderColor: color }}
    >
      <div className="mb-1 font-title text-[0.68rem] font-bold uppercase tracking-[0.12em]" style={{ color }}>
        {label}
      </div>
      <p className={`text-sm leading-relaxed ${muted ? "text-slate-soft" : "text-snow"}`}>{children}</p>
    </div>
  );
}

function ConversationMockup() {
  return (
    <div className="flex h-full min-h-[26rem] flex-col justify-between">
      <div className="space-y-4">
        <ChatBubble label="hablante 1" color="#A89AE8">
          Podemos movernos a una mesa más tranquila para explicar el prototipo.
        </ChatBubble>
        <ChatBubble side="right" label="hablante 2" color="#7C9A88">
          Sí, SENDA ya marcó quién está hablando y mantiene el hilo.
        </ChatBubble>
        <ChatBubble label="en vivo" color="#A89AE8" muted>
          Estoy probando la transcripción en tiempo real
          <span className="ml-1 inline-block h-2 w-2 animate-pulse rounded-full bg-aura align-middle" />
        </ChatBubble>
      </div>

      <div className="rounded-2xl border border-line bg-panel px-4 py-3">
        <div className="mb-2 flex items-center justify-between text-xs text-slate-soft">
          <span>3 hablantes detectados</span>
          <span className="flex items-center gap-1 text-aura">
            <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-aura" />
            activo
          </span>
        </div>
        <div className="grid grid-cols-3 gap-2">
          <span className="h-1.5 rounded-full bg-aura" />
          <span className="h-1.5 rounded-full bg-[#7C9A88]" />
          <span className="h-1.5 rounded-full bg-[#9A8484]" />
        </div>
      </div>
    </div>
  );
}

function Person({ active = false, className = "" }) {
  return (
    <div className={`absolute flex flex-col items-center ${className}`}>
      <div
        className={`grid h-20 w-20 place-items-center rounded-full border ${
          active
            ? "border-aura bg-aura/10 shadow-[0_0_42px_rgba(168,154,232,.5)]"
            : "border-white/10 bg-white/5"
        }`}
      >
        <div className="h-8 w-8 rounded-full bg-slate-soft/45" />
      </div>
      <div className="mt-[-0.35rem] h-20 w-28 rounded-t-[2.8rem] border border-white/10 bg-white/5" />
    </div>
  );
}

function CameraMockup() {
  return (
    <div className="relative min-h-[26rem] overflow-hidden rounded-2xl border border-line bg-[radial-gradient(circle_at_50%_18%,rgba(168,154,232,.2),transparent_34%),#050505]">
      <div className="absolute left-4 top-4 rounded-full border border-white/10 bg-black/45 px-3 py-1 text-xs text-slate-soft">
        camara activa
      </div>
      <Person className="left-4 top-24 scale-90 opacity-70" />
      <Person active className="left-1/2 top-16 -translate-x-1/2" />
      <Person className="right-4 top-28 scale-90 opacity-70" />

      <div className="absolute left-1/2 top-[15.5rem] w-[14rem] -translate-x-1/2 rounded-2xl border border-aura/45 bg-black/75 px-4 py-3 shadow-[0_16px_60px_rgba(0,0,0,.45)]">
        <div className="mb-1 font-title text-[0.68rem] font-bold uppercase tracking-[0.12em] text-aura">
          hablante activo
        </div>
        <p className="text-sm leading-relaxed text-snow">"La demo ya reconoce mi posición."</p>
      </div>

      <div className="absolute bottom-4 left-4 right-4 flex items-center justify-between rounded-2xl bg-panel/90 px-4 py-3 text-xs text-slate-soft">
        <span>direccion de voz</span>
        <span className="text-aura">centro</span>
      </div>
    </div>
  );
}

function VoiceMockup() {
  return (
    <div className="flex h-full min-h-[26rem] flex-col justify-between">
      <div className="rounded-2xl border border-[#7C9A88]/40 bg-[#7C9A88]/10 p-4">
        <div className="mb-3 flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-full bg-[#7C9A88]/20 text-[#7C9A88]">
            <svg viewBox="0 0 24 24" aria-hidden="true" className="h-5 w-5 fill-none stroke-current stroke-2">
              <path d="M4 10v4h4l5 4V6L8 10z" />
              <path d="M16 9c1 .8 1.5 1.8 1.5 3S17 14.2 16 15" />
              <path d="M18.5 7a7 7 0 0 1 0 10" />
            </svg>
          </div>
          <div>
            <div className="font-title text-xs font-bold uppercase tracking-[0.12em] text-[#7C9A88]">
              voz reproducida
            </div>
            <div className="text-sm text-snow">"Estoy de acuerdo con esa idea."</div>
          </div>
        </div>
        <div className="flex h-8 items-end gap-1.5" aria-hidden="true">
          {[35, 58, 44, 76, 52, 66, 40, 60, 32].map((height, index) => (
            <span
              key={index}
              className="w-2 rounded-full bg-[#7C9A88]"
              style={{ height: `${height}%`, opacity: 0.45 + index * 0.04 }}
            />
          ))}
        </div>
      </div>

      <div className="space-y-3">
        <div className="rounded-2xl bg-panel-soft px-4 py-3 text-sm text-slate-soft">
          Respuesta sugerida: "¿Puedes repetir la última parte?"
        </div>
        <div className="flex items-center gap-2 rounded-full border border-line bg-panel p-2">
          <div className="min-w-0 flex-1 px-3 text-sm text-slate-soft">Escribe algo...</div>
          <button className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-voice text-night" type="button" aria-label="Enviar">
            <svg viewBox="0 0 24 24" aria-hidden="true" className="h-4 w-4 fill-none stroke-current stroke-[2.4]">
              <path d="M5 12h14" />
              <path d="m13 6 6 6-6 6" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  );
}

function MockupContent({ mockup }) {
  if (mockup === "camera") return <CameraMockup />;
  if (mockup === "voice") return <VoiceMockup />;
  return <ConversationMockup />;
}

export default function AppShowcase({ mockup = "conversation", reverse = false }) {
  const copy = showcaseCopy[mockup] ?? showcaseCopy.conversation;
  const textOrder = reverse ? "md:order-2" : "md:order-1";
  const panelOrder = reverse ? "md:order-1" : "md:order-2";

  return (
    <section className="bg-night py-20 sm:py-24" aria-labelledby={`showcase-${mockup}`}>
      <div className="mx-auto grid max-w-6xl items-center gap-10 px-6 md:grid-cols-2 md:gap-14">
        <Reveal className={`order-1 ${textOrder}`}>
          <span className="font-title text-xs font-bold uppercase tracking-[0.16em] text-aura">
            {copy.eyebrow}
          </span>
          <h2 id={`showcase-${mockup}`} className="mt-4 max-w-xl font-title text-3xl font-bold leading-tight text-snow sm:text-4xl">
            {copy.title}
          </h2>
          <p className="mt-5 max-w-xl text-lg leading-relaxed text-slate-soft">{copy.description}</p>
        </Reveal>

        <Reveal className={`order-2 ${panelOrder}`}>
          <div className="relative overflow-hidden rounded-3xl border border-white/10 bg-[#eef2ff] p-4 shadow-[0_36px_120px_rgba(0,0,0,.38)] sm:p-7">
            <div
              className="absolute inset-0"
              aria-hidden="true"
              style={{
                background:
                  "linear-gradient(135deg, rgba(107,127,232,.72) 0%, rgba(168,154,232,.58) 48%, rgba(255,255,255,.72) 100%)",
              }}
            />
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_20%,rgba(255,255,255,.7),transparent_34%),radial-gradient(circle_at_75%_30%,rgba(107,127,232,.45),transparent_35%)]" aria-hidden="true" />
            <div className="relative flex min-h-[34rem] items-center justify-center">
              <AppMockupCard title={mockup === "camera" ? "Cámara" : mockup === "voice" ? "Texto a voz" : "Conversación"}>
                <MockupContent mockup={mockup} />
              </AppMockupCard>
            </div>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
