import { useEffect, useRef, useState } from "react";

import agriculturalRobotGreenhouse from "../../assets/dashboard/device-status/agricultural-robot-greenhouse.jpg";
import automatedIrrigation from "../../assets/dashboard/device-status/automated-irrigation.jpg";
import embeddedCircuitBoard from "../../assets/dashboard/device-status/embedded-circuit-board.jpg";
import greenhouseSensorEnvironment from "../../assets/dashboard/device-status/greenhouse-sensor-environment.jpg";
import industrialIotMachinery from "../../assets/dashboard/device-status/industrial-iot-machinery.jpg";
import industrialRobotArm from "../../assets/dashboard/device-status/industrial-robot-arm.jpg";
import smartBulbRoom from "../../assets/dashboard/device-status/smart-bulb-room.jpg";
import smartEnergyMeter from "../../assets/dashboard/device-status/smart-energy-meter.jpg";
import smartHomeController from "../../assets/dashboard/device-status/smart-home-controller.jpg";
import wirelessFieldSensor from "../../assets/dashboard/device-status/wireless-field-sensor.jpg";

const scenes = [
  {
    image: agriculturalRobotGreenhouse,
    alt: "Agricultural robot working among greenhouse crops",
    caption: "Agricultural automation",
    description: "A real-world example of connected growing systems.",
    sourceName: "Wikimedia Commons",
    sourceUrl: "https://commons.wikimedia.org/wiki/File:FarmBot_Genesis_in_a_greenhouse.jpg",
  },
  {
    image: industrialRobotArm,
    alt: "Industrial robot arm operating machinery in a factory",
    caption: "Industrial robotics",
    description: "Connected automation in a manufacturing environment.",
    sourceName: "Wikimedia Commons",
    sourceUrl: "https://commons.wikimedia.org/wiki/File:Robot_arm_operates_machine_in_factory_setting.jpg",
  },
  {
    image: smartBulbRoom,
    alt: "Philips Hue smart-light hub and two connected bulbs",
    caption: "Smart lighting",
    description: "Everyday environments are becoming more connected.",
    sourceName: "Wikimedia Commons",
    sourceUrl: "https://commons.wikimedia.org/wiki/File:Philips_Hue_hub_and_2_bulbs.jpg",
  },
  {
    image: smartHomeController,
    alt: "Wi-Fi enabled smart home controller electronics",
    caption: "Connected homes",
    description: "Embedded controllers help devices work together.",
    sourceName: "Wikimedia Commons",
    sourceUrl: "https://commons.wikimedia.org/wiki/File:EHouse_WiFi_(IOT)_Smart_home_controller_DIY.jpg",
  },
  {
    image: greenhouseSensorEnvironment,
    alt: "Greenhouse growing beds used for environmental monitoring",
    caption: "Environmental sensing",
    description: "Sensors help make growing conditions visible.",
    sourceName: "Wikimedia Commons",
    sourceUrl: "https://commons.wikimedia.org/wiki/File:Veteran_New_Farmer_Green_Bexar_Farm_(20201017-NRCS-LSC-0250).jpg",
  },
  {
    image: automatedIrrigation,
    alt: "Automated irrigation reel watering an agricultural field",
    caption: "Smart irrigation",
    description: "Automation supports more deliberate resource use.",
    sourceName: "Wikimedia Commons",
    sourceUrl: "https://commons.wikimedia.org/wiki/File:AutomaticlyOperatingIrrigationReel.jpg",
  },
  {
    image: industrialIotMachinery,
    alt: "Industrial welding robots arranged in a factory",
    caption: "Machine monitoring",
    description: "Industrial systems rely on connected equipment data.",
    sourceName: "Wikimedia Commons",
    sourceUrl: "https://commons.wikimedia.org/wiki/File:FANUC_6-axis_welding_robots.jpg",
  },
  {
    image: smartEnergyMeter,
    alt: "Digital smart electrical meter close-up",
    caption: "Energy visibility",
    description: "Smart meters make consumption easier to observe.",
    sourceName: "Wikimedia Commons",
    sourceUrl: "https://commons.wikimedia.org/wiki/File:Smart_Electrical_Meter.jpg",
  },
  {
    image: embeddedCircuitBoard,
    alt: "Close-up photograph of an electronic circuit board",
    caption: "Embedded electronics",
    description: "Small sensing hardware forms the foundation of IoT.",
    sourceName: "Wikimedia Commons",
    sourceUrl: "https://commons.wikimedia.org/wiki/File:Circuit_board_Closeup_(24884297897).jpg",
  },
  {
    image: wirelessFieldSensor,
    alt: "Wireless irrigation monitoring sensors positioned in a farm field",
    caption: "Field intelligence",
    description: "Distributed sensors connect real environments to data.",
    sourceName: "Wikimedia Commons",
    sourceUrl: "https://commons.wikimedia.org/wiki/File:Drought_Mitigation_-_Reiter_Berry_Farms_-_Wireless_Irrigation_monitoring_Network_(20150827-NRCS-LSC-0249).jpg",
  },
];

export default function DeviceStatusVisual() {
  const [activeIndex, setActiveIndex] = useState(0);
  const [visible, setVisible] = useState(true);
  const [imageFailed, setImageFailed] = useState(false);
  const fadeTimeoutRef = useRef(null);

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setVisible(false);
      fadeTimeoutRef.current = window.setTimeout(() => {
        setActiveIndex((currentIndex) => (currentIndex + 1) % scenes.length);
        setImageFailed(false);
        setVisible(true);
      }, 350);
    }, 20000);

    return () => {
      window.clearInterval(intervalId);
      if (fadeTimeoutRef.current) window.clearTimeout(fadeTimeoutRef.current);
    };
  }, []);

  const scene = scenes[activeIndex];

  function selectScene(index) {
    if (fadeTimeoutRef.current) window.clearTimeout(fadeTimeoutRef.current);
    setVisible(false);
    fadeTimeoutRef.current = window.setTimeout(() => {
      setActiveIndex(index);
      setImageFailed(false);
      setVisible(true);
    }, 200);
  }

  return (
    <div className="relative mt-5 min-h-[190px] min-w-0 flex-1 overflow-hidden rounded-[12px] border border-[#e1edf1] bg-[#f8fbfd] sm:min-h-[210px]" role="region" aria-label="NexaIoT IoT visual highlights">
      {imageFailed ? (
        <div className="flex h-full w-full items-center justify-center bg-[#eaf7fa] px-6 text-center text-[11px] font-medium text-[#527180]">
          Visual unavailable
        </div>
      ) : (
        <img
          src={scene.image}
          alt={scene.alt}
          onError={() => setImageFailed(true)}
          className={`h-full w-full object-cover motion-safe:transition-opacity motion-safe:duration-700 motion-safe:ease-out motion-reduce:transition-none ${visible ? "opacity-100" : "opacity-0"}`}
        />
      )}
      <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-[#102a3a]/75 via-[#102a3a]/25 to-transparent px-4 pb-7 pt-12">
        <p className="text-[11px] font-semibold text-white">{scene.caption}</p>
        <p className="mt-1 text-[9px] text-white/80">{scene.description}</p>
      </div>
      <div className="absolute inset-x-0 bottom-2 flex justify-center gap-1.5" aria-label="Choose visual">
        {scenes.map((item, index) => (
          <button
            key={item.image}
            type="button"
            onClick={() => selectScene(index)}
            aria-label={`Show ${item.caption}`}
            aria-current={index === activeIndex ? "true" : undefined}
            className={`h-1.5 rounded-full transition-all focus:outline-none focus:ring-2 focus:ring-white/80 motion-reduce:transition-none ${index === activeIndex ? "w-4 bg-white" : "w-1.5 bg-white/60 hover:bg-white/90"}`}
          />
        ))}
      </div>
    </div>
  );
}
