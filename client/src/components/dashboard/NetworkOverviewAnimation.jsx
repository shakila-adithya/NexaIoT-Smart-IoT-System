import { useEffect, useRef } from "react";

export default function NetworkOverviewAnimation() {
  const containerRef = useRef(null);

  useEffect(() => {
    let animation;
    let cancelled = false;

    async function loadAnimation() {
      if (!containerRef.current) return;

      const [{ default: lottie }, { default: animationData }] = await Promise.all([
        import("lottie-web"),
        import("../../assets/dashboard/network-overview-lottie.json"),
      ]);

      if (cancelled || !containerRef.current) return;

      animation = lottie.loadAnimation({
        container: containerRef.current,
        renderer: "svg",
        loop: true,
        autoplay: true,
        animationData,
        rendererSettings: {
          preserveAspectRatio: "xMidYMid meet",
          progressiveLoad: true,
        },
      });

      if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        animation.goToAndStop(0, true);
      }
    }

    loadAnimation();

    return () => {
      cancelled = true;
      animation?.destroy();
    };
  }, []);

  return <div ref={containerRef} className="network-overview-illustration" aria-hidden="true" />;
}
