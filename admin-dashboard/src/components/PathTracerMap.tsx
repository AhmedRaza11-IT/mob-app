'use client';

import React, { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Polyline, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

export interface LocationPoint {
  id: string;
  latitude: number;
  longitude: number;
  accuracy: number;
  formattedAddress: string;
  city?: string;
  country?: string;
  timestamp: string;
}

interface PathTracerMapProps {
  points: LocationPoint[];
}

function MapBoundsUpdater({ points }: { points: LocationPoint[] }) {
  const map = useMap();
  useEffect(() => {
    if (!points || points.length === 0) return;
    if (points.length === 1) {
      map.setView([points[0].latitude, points[0].longitude], 15);
    } else {
      const bounds = L.latLngBounds(points.map((p) => [p.latitude, p.longitude]));
      map.fitBounds(bounds, { padding: [40, 40] });
    }
  }, [points, map]);

  return null;
}

export default function PathTracerMap({ points }: PathTracerMapProps) {
  // Empty state
  if (!points || points.length === 0) {
    return (
      <div className="h-72 w-full rounded-xl bg-slate-50 border border-dashed border-slate-200 flex flex-col items-center justify-center p-6 text-center">
        <div className="w-10 h-10 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 mb-2">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
          </svg>
        </div>
        <p className="text-xs font-semibold text-slate-600">No location records found for this period.</p>
        <p className="text-[11px] text-slate-400 mt-0.5">Select a wider timeframe (e.g. 6 Hours or 24 Hours) to trace historical path.</p>
      </div>
    );
  }

  const pathCoordinates: [number, number][] = points.map((p) => [p.latitude, p.longitude]);
  const centerCoordinate: [number, number] = [
    points[points.length - 1].latitude,
    points[points.length - 1].longitude,
  ];

  const waypointIcon = (idx: number, isLatest: boolean) => {
    if (isLatest) {
      return L.divIcon({
        className: 'custom-latest-pin',
        html: `
          <div style="position: relative; width: 34px; height: 34px; display: flex; align-items: center; justify-content: center;">
            <div style="position: absolute; inset: 0; border-radius: 50%; background-color: #10B981; opacity: 0.45; animation: ping 1.5s cubic-bezier(0, 0, 0.2, 1) infinite;"></div>
            <div style="position: relative; width: 22px; height: 22px; border-radius: 50%; background-color: #10B981; border: 2.5px solid white; box-shadow: 0 3px 8px rgba(0,0,0,0.35); display: flex; align-items: center; justify-content: center; color: white; font-size: 10px; font-weight: 800;">
              ${idx}
            </div>
          </div>
        `,
        iconSize: [34, 34],
        iconAnchor: [17, 17],
        popupAnchor: [0, -17],
      });
    }

    return L.divIcon({
      className: 'custom-waypoint-pin',
      html: `
        <div style="width: 24px; height: 24px; border-radius: 50%; background-color: #6C3AEB; border: 2px solid white; box-shadow: 0 2px 5px rgba(0,0,0,0.25); display: flex; align-items: center; justify-content: center; color: white; font-size: 10px; font-weight: 700;">
          ${idx}
        </div>
      `,
      iconSize: [24, 24],
      iconAnchor: [12, 12],
      popupAnchor: [0, -12],
    });
  };

  return (
    <div className="relative">
      <div className="h-72 w-full rounded-xl overflow-hidden border border-slate-200 shadow-inner z-0">
        <MapContainer
          center={centerCoordinate}
          zoom={15}
          scrollWheelZoom={false}
          className="h-full w-full"
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />

          <MapBoundsUpdater points={points} />

          {/* Polyline connecting all points chronologically */}
          {points.length > 1 && (
            <Polyline
              positions={pathCoordinates}
              pathOptions={{
                color: '#6C3AEB',
                weight: 4,
                dashArray: '6, 8',
                opacity: 0.85,
              }}
            />
          )}

          {/* Markers */}
          {points.map((pt, idx) => {
            const isLatest = idx === points.length - 1;
            return (
              <Marker
                key={pt.id || `pt-${idx}`}
                position={[pt.latitude, pt.longitude]}
                icon={waypointIcon(idx + 1, isLatest)}
              >
                <Popup>
                  <div className="text-xs p-1 max-w-[200px]">
                    <div className="flex items-center justify-between gap-2 mb-1">
                      <span className="font-bold text-slate-800">
                        {isLatest ? `📍 Point ${idx + 1} (Latest)` : `Waypoint #${idx + 1}`}
                      </span>
                      <span className="text-[10px] text-purple-600 font-semibold">
                        ±{Math.round(pt.accuracy)}m
                      </span>
                    </div>
                    <p className="text-slate-600 text-[11px] leading-tight mb-1">{pt.formattedAddress}</p>
                    <p className="text-[10px] text-slate-400 font-mono">
                      {new Date(pt.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                    </p>
                  </div>
                </Popup>
              </Marker>
            );
          })}
        </MapContainer>
      </div>

      {points.length === 1 && (
        <div className="mt-2 px-3 py-1.5 bg-amber-50 border border-amber-200 rounded-lg text-[11px] text-amber-700 flex items-center gap-1.5">
          <svg className="w-3.5 h-3.5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          Only 1 ping recorded in this timeframe. No movement path available.
        </div>
      )}
    </div>
  );
}
