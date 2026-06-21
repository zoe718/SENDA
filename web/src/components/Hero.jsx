import { useEffect, useRef } from "react";

export default function Hero() {
  const videoRef = useRef(null);

  // Respeta prefers-reduced-motion: no autoreproducir el video de fondo.
  useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduce && videoRef.current) {
      videoRef.current.removeAttribute("autoplay");
      videoRef.current.pause();
    }
  }, []);

  return (
    <section
      id="top"
      className="relative grid min-h-[100svh] place-items-center overflow-hidden text-center"
      aria-labelledby="hero-title"
    >
      {/* Video de fondo */}
      <video
        ref={videoRef}
        className="absolute inset-0 h-full w-full object-cover"
        autoPlay
        muted
        loop
        playsInline
        preload="auto"
        aria-hidden="true"
      >
        <source src="/resources/gatitos.mp4" type="video/mp4" />
      </video>

      {/* Capa oscura para legibilidad */}
      <div
        className="absolute inset-0"
        aria-hidden="true"
        style={{
          background: "rgba(0,0,0,.76)",
        }}
      />

      <div
        className="pointer-events-none absolute left-[-7rem] top-[18%] h-[34rem] w-[22rem] rounded-full opacity-35 blur-3xl sm:left-[-2rem] md:left-[4%]"
        aria-hidden="true"
        style={{
          background:
            "linear-gradient(135deg, rgba(107,127,232,.55) 0%, rgba(168,154,232,.42) 100%)",
          mixBlendMode: "screen",
        }}
      />

      {/* Contenido */}
      <div className="relative z-10 flex flex-col items-center gap-10 px-6">
        <h1
          id="hero-title"
          className="animate-hero-in max-w-[16ch] font-title text-5xl font-extrabold leading-[1.05] text-snow sm:text-6xl md:text-7xl"
          style={{ textShadow: "0 4px 30px rgba(0,0,0,.45)" }}
        >
          Haz <span className="highlight-gradient-text">visible</span> la conversación
        </h1>

        {/* CTA debajo del texto, con indicación hacia abajo */}
        <a
          href="#about"
          className="animate-cue group inline-flex items-center gap-2 rounded-full px-7 py-3.5 font-title text-base font-semibold text-night shadow-[0_12px_34px_rgba(107,127,232,.28)] transition-transform duration-300 hover:-translate-y-0.5"
          style={{
            background: "linear-gradient(135deg, #6b7fe8 0%, #a89ae8 100%)",
          }}
        >
          Comunícate
          <svg
            viewBox="0 0 24 24"
            aria-hidden="true"
            className="h-5 w-5 fill-none stroke-current stroke-[2.4]"
            style={{ strokeLinecap: "round", strokeLinejoin: "round" }}
          >
            <path d="M6 9l6 6 6-6" />
          </svg>
        </a>
      </div>
    </section>
  );
}
