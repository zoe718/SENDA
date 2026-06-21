import Header from "./components/Header.jsx";
import Hero from "./components/Hero.jsx";
import About from "./components/About.jsx";
import HowItWorks from "./components/HowItWorks.jsx";
import Footer from "./components/Footer.jsx";

export default function App() {
  return (
    <>
      <a
        href="#about"
        className="sr-only focus:not-sr-only focus:absolute focus:left-3 focus:top-3 focus:z-[200] focus:rounded-lg focus:bg-panel focus:px-4 focus:py-2 focus:text-snow"
      >
        Saltar al contenido
      </a>
      <Header />
      <main>
        <Hero />
        <About />
        <HowItWorks />
      </main>
      <Footer />
    </>
  );
}
