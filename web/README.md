# SENDA — Landing page

> **Haz visible la conversación.**
> Landing de SENDA construida con **Vite + React + Tailwind CSS v4**.

Vive en `web/` para no interferir con la app Android del equipo (`mobile/`).

## Requisitos

- Node.js 18+ y npm (ya instalados en este equipo: Node v24, npm v11).

## Correr en local

```bash
cd web
npm install      # solo la primera vez
npm run dev      # servidor de desarrollo en http://localhost:5173
```

Otros scripts:

```bash
npm run build    # build de producción → web/dist
npm run preview  # sirve el build de producción
```

## Estructura

```
web/
├── index.html                 ← entrada de Vite (fuentes + #root)
├── vite.config.js             ← plugins React + Tailwind v4
├── public/
│   └── resources/gatitos.mp4  ← video de fondo del hero
└── src/
    ├── main.jsx               ← punto de entrada React
    ├── App.jsx                ← composición de secciones
    ├── index.css              ← Tailwind v4 + tokens de marca (@theme) + animaciones
    └── components/
        ├── Header.jsx         ← header minimalista (solo logo SENDA)
        ├── Hero.jsx           ← video de fondo + CTA "Haz visible la conversación"
        ├── About.jsx          ← qué es SENDA y sus capacidades
        ├── HowItWorks.jsx     ← los 3 pasos
        ├── Footer.jsx         ← footer con datos del proyecto
        └── Reveal.jsx         ← animación reutilizable al hacer scroll
```

## Branding

- **Nombre:** SENDA.
- **Tipografía:** Sora (títulos) + Inter (texto), vía Google Fonts.
- **Paleta** (tokens en `src/index.css`, usables como `bg-navy`, `text-aura`, etc.):
  - Verde aura `#42E8B4` · Azul voz `#57B7FF` · Navy `#0B1736`
  - Blanco nieve `#F8FCFF` · Menta `#EFFFFA` · Gris texto `#64748B` · Coral `#FF6B6B`
  - Degradado de marca: `linear-gradient(135deg, #42E8B4 0%, #57B7FF 100%)`

### Cambiar el logo

En `src/components/Header.jsx` (y opcionalmente `Footer.jsx`), reemplaza el
`<span className="brand-gradient ...">` por tu logo:

```jsx
<img src="/resources/tu-logo.svg" alt="SENDA" className="h-9 w-auto" />
```

Coloca el archivo del logo en `web/public/resources/`.

## Accesibilidad

- No depende solo del color (aura + etiqueta + ícono).
- `prefers-reduced-motion`: desactiva animaciones y pausa el video de fondo.
- Skip link, `:focus-visible`, HTML semántico, alto contraste.
- Lenguaje inclusivo: *personas sordas*, *con discapacidad auditiva* y *personas oyentes*.

## Desplegar

100% estático tras `npm run build`:

- **Vercel / Netlify:** root = `web`, build command `npm run build`, output `dist`.
- Luego actualiza `deploy-url` en `../platanus-hack-project.jsonc`.
