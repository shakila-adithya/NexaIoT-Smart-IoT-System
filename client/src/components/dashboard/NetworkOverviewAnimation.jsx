import { useEffect, useRef } from "react";
import lottie from "lottie-web";
import animationData from "../../assets/dashboard/network-overview-lottie.json";

export default function NetworkOverviewAnimation() {
  const containerRef = useRef(null);

  useEffect(() => {
    if (!containerRef.current) return undefined;

    const animation = lottie.loadAnimation({
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

    return () => animation.destroy();
  }, []);

  return <div ref={containerRef} className="network-overview-illustration" aria-hidden="true" />;
}
