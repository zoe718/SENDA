# Voxi — Landing page

Landing estática (HTML + CSS + JS, sin dependencias ni paso de build) para
presentar **Voxi**: *haz visible la conversación*.

> Vive en `web/` para no interferir con la app Android del equipo (`mobile/`).

## Estructura

```
web/
├── index.html   ← marcado de todas las secciones
├── styles.css   ← paleta oficial, degradado, animaciones y responsive
├── script.js    ← menú móvil, reveal al scroll y simulación del demo
└── README.md
```

Las secciones del HTML mapean 1:1 a los componentes propuestos:
`Navbar`, `HeroSection` (mockup AR), `ProblemSection`, `SolutionSection`,
`HowItWorks`, `Features`, `SpeakerDemo` (phone interactivo), `CTASection`, `Footer`.

## Correr localmente

No requiere Node. Tres opciones:

1. **Doble clic** en `index.html` (lo abre el navegador).
2. **Servidor estático con Python** (recomendado, evita restricciones de `file://`):
   ```bash
   cd web
   python -m http.server 5173
   # luego abre http://localhost:5173
   ```
3. Cualquier extensión tipo *Live Server* de tu editor.

## Desplegar

Es 100% estático, así que se publica en segundos:

- **Vercel / Netlify:** arrastra la carpeta `web/`, o conecta tu repo personal
  (ver nota de deploy en el README raíz) y define `web` como *root directory*.
- **GitHub Pages:** sirve la carpeta `web/`.

Después actualiza `deploy-url` en `../platanus-hack-project.jsonc`.

## Branding

- **Tipografía:** Sora (títulos) + Inter (texto), vía Google Fonts.
- **Paleta** (definida como variables CSS en `styles.css`):
  - Verde aura `#42E8B4` · Azul voz `#57B7FF` · Navy `#0B1736`
  - Blanco nieve `#F8FCFF` · Menta `#EFFFFA` · Gris texto `#64748B` · Coral `#FF6B6B`
  - Degradado principal: `linear-gradient(135deg, #42E8B4 0%, #57B7FF 100%)`

## Accesibilidad

- No depende solo del color: el hablante activo lleva **aura + etiqueta de texto + ícono de onda**.
- `prefers-reduced-motion` desactiva animaciones y arranca el demo en pausa.
- Skip link, `:focus-visible`, roles/`aria-live`, alto contraste y targets táctiles grandes.
- Lenguaje inclusivo: *personas sordas*, *con discapacidad auditiva* y *personas oyentes*.
