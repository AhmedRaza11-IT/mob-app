import React from 'react';

interface VibeSyncLogoProps {
  className?: string;
  size?: number;
}

export default function VibeSyncLogo({ className = 'w-9 h-9', size }: VibeSyncLogoProps) {
  const sizeStyle = size ? { width: size, height: size } : undefined;

  return (
    <div
      className={`rounded-xl overflow-hidden shadow-md flex-shrink-0 flex items-center justify-center ${className}`}
      style={sizeStyle}
    >
      <svg
        viewBox="0 0 108 108"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        className="w-full h-full"
      >
        <defs>
          <linearGradient id="vibeSyncBgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#6C3AEB" />
            <stop offset="60%" stopColor="#3A6BEB" />
            <stop offset="100%" stopColor="#07B7A0" />
          </linearGradient>
        </defs>

        {/* Brand Background Gradient */}
        <rect width="108" height="108" fill="url(#vibeSyncBgGrad)" />

        {/* Gradient overlays matching Android launcher background */}
        <path d="M108 0 L108 60 L50 0 Z" fill="#3A6BEB" fillOpacity="0.45" />
        <path d="M0 108 L108 108 L108 55 Z" fill="#07B7A0" fillOpacity="0.35" />

        {/* Chat bubble outline from ic_launcher_foreground */}
        <path
          fill="#FFFFFF"
          d="M54 18 C33.6 18 17 32.8 17 51 C17 58.5 19.6 65.4 24.2 71 L20 90 L39.5 84.8 C43.9 86.2 48.8 87 54 87 C74.4 87 91 72.2 91 54 C91 35.8 74.4 21 54 21 Z M54 24 C72.8 24 88 37.6 88 54 C88 70.4 72.8 84 54 84 C49.2 84 44.6 83.2 40.4 81.6 L38.8 81 L27.6 84.4 L31 73.8 L30.2 72.8 C25.6 67.4 22.8 61.4 22.8 54 C22.8 37.6 36.14 24 54 24 Z"
        />

        {/* Waveform bar 1 (leftmost, short) */}
        <path
          fill="#FFFFFF"
          d="M34 49 L34 59 Q34 61 36 61 Q38 61 38 59 L38 49 Q38 47 36 47 Q34 47 34 49 Z"
        />

        {/* Waveform bar 2 (medium-tall) */}
        <path
          fill="#FFFFFF"
          d="M42 44 L42 64 Q42 66 44 66 Q46 66 46 64 L46 44 Q46 42 44 42 Q42 42 42 44 Z"
        />

        {/* Waveform bar 3 (tallest, center) */}
        <path
          fill="#FFFFFF"
          d="M50 39 L50 69 Q50 71 52 71 Q54 71 54 69 L54 39 Q54 37 52 37 Q50 37 50 39 Z"
        />

        {/* Waveform bar 4 (medium-tall) */}
        <path
          fill="#FFFFFF"
          d="M58 44 L58 64 Q58 66 60 66 Q62 66 62 64 L62 44 Q62 42 60 42 Q58 42 58 44 Z"
        />

        {/* Waveform bar 5 (rightmost, short) */}
        <path
          fill="#FFFFFF"
          d="M66 49 L66 59 Q66 61 68 61 Q70 61 70 59 L70 49 Q70 47 68 47 Q66 47 66 49 Z"
        />
      </svg>
    </div>
  );
}
