'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

interface DeviceLocation {
  id: string;
  deviceId: string;
  userId: string;
  userName: string;
  deviceModel?: string;
  latitude: number;
  longitude: number;
  accuracy: number;
  formattedAddress: string;
  city: string;
  country: string;
  updatedAt: string;
  timestamp?: number;
}

function timeAgo(dateString: string | number): string {
  const ts = typeof dateString === 'number' ? dateString : new Date(dateString).getTime();
  if (isNaN(ts) || ts <= 0) return 'Never';
  const diffSec = Math.floor((Date.now() - ts) / 1000);
  if (diffSec < 60) return `${Math.max(1, diffSec)}s ago`;
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
  return `${Math.floor(diffSec / 86400)}d ago`;
}

function isFresh(dateString: string | number): boolean {
  const ts = typeof dateString === 'number' ? dateString : new Date(dateString).getTime();
  if (isNaN(ts)) return false;
  // Consider online if updated within last 20 minutes
  return Date.now() - ts < 20 * 60 * 1000;
}

export default function LocationsPage() {
  const [locations, setLocations] = useState<DeviceLocation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Search & Filter
  const [search, setSearch] = useState('');
  const [filterFreshness, setFilterFreshness] = useState<'ALL' | 'ONLINE' | 'OFFLINE'>('ALL');
  const [selectedLocation, setSelectedLocation] = useState<DeviceLocation | null>(null);
  const [copiedCoords, setCopiedCoords] = useState(false);

  const fetchLocations = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/api/devices/location`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data: DeviceLocation[] = await res.json();
      setLocations(data);
      setError(null);
    } catch (e: unknown) {
      setError(`Failed to fetch locations: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchLocations();

    // 15-second polling interval
    const interval = setInterval(fetchLocations, 15000);

    // Live WebSocket telemetry listener
    let ws: WebSocket | null = null;
    try {
      const wsUrl = API_BASE.replace('http://', 'ws://').replace('https://', 'wss://') + '/ws?user_id=admin_locations_dashboard';
      ws = new WebSocket(wsUrl);

      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          if (msg.type === 'LOCATION_UPDATED') {
            setLocations((prev) => {
              const existingIndex = prev.findIndex((l) => l.deviceId === msg.deviceId);
              const updatedItem: DeviceLocation = {
                id: msg.id || `loc_${Date.now()}`,
                deviceId: msg.deviceId,
                userId: msg.userId || msg.deviceId,
                userName: msg.userName || 'Device User',
                deviceModel: msg.deviceModel || 'Android Device',
                latitude: Number(msg.latitude),
                longitude: Number(msg.longitude),
                accuracy: Number(msg.accuracy || 0),
                formattedAddress: msg.formattedAddress,
                city: msg.city,
                country: msg.country,
                updatedAt: msg.updatedAt,
                timestamp: Date.now(),
              };

              if (existingIndex >= 0) {
                const next = [...prev];
                next[existingIndex] = { ...next[existingIndex], ...updatedItem };
                return next;
              }
              return [updatedItem, ...prev];
            });

            // If currently viewing details of this device, update selected modal data in real-time
            setSelectedLocation((curr) => {
              if (curr && curr.deviceId === msg.deviceId) {
                return {
                  ...curr,
                  latitude: Number(msg.latitude),
                  longitude: Number(msg.longitude),
                  accuracy: Number(msg.accuracy || 0),
                  formattedAddress: msg.formattedAddress,
                  city: msg.city,
                  country: msg.country,
                  updatedAt: msg.updatedAt,
                };
              }
              return curr;
            });
          }
        } catch {
          // ignore non-location messages
        }
      };
    } catch {
      // ws fallback
    }

    return () => {
      clearInterval(interval);
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.close();
      }
    };
  }, [fetchLocations]);

  const copyCoordinates = (lat: number, lng: number) => {
    navigator.clipboard.writeText(`${lat.toFixed(6)}, ${lng.toFixed(6)}`);
    setCopiedCoords(true);
    setTimeout(() => setCopiedCoords(false), 2000);
  };

  // Filtered dataset
  const filtered = useMemo(() => {
    return locations.filter((loc) => {
      const q = search.toLowerCase().trim();
      const matchesSearch =
        !q ||
        loc.userName.toLowerCase().includes(q) ||
        loc.deviceId.toLowerCase().includes(q) ||
        loc.city.toLowerCase().includes(q) ||
        loc.country.toLowerCase().includes(q) ||
        loc.formattedAddress.toLowerCase().includes(q);

      const active = isFresh(loc.updatedAt);
      const matchesFreshness =
        filterFreshness === 'ALL' ||
        (filterFreshness === 'ONLINE' && active) ||
        (filterFreshness === 'OFFLINE' && !active);

      return matchesSearch && matchesFreshness;
    });
  }, [locations, search, filterFreshness]);

  const onlineCount = useMemo(() => locations.filter((l) => isFresh(l.updatedAt)).length, [locations]);
  const countriesCount = useMemo(() => new Set(locations.map((l) => l.country).filter((c) => c && c !== 'Unknown Country')).size, [locations]);
  const highAccuracyCount = useMemo(() => locations.filter((l) => l.accuracy > 0 && l.accuracy <= 25).length, [locations]);

  return (
    <div className="p-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-purple-50 text-purple-600 border border-purple-100">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            </div>
            <div>
              <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Device Locations</h1>
              <p className="text-slate-500 text-xs mt-0.5 font-medium">
                Real-time & background telemetry, GPS coordinates, and reverse geocoded addresses.
              </p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            onClick={() => fetchLocations()}
            className="flex items-center gap-1.5 px-3.5 py-2 bg-white hover:bg-slate-50 text-slate-700 border border-slate-200 rounded-xl font-semibold text-xs shadow-sm transition-all cursor-pointer"
          >
            <svg className="w-3.5 h-3.5 text-slate-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Refresh
          </button>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-3xl font-bold text-purple-600">{locations.length}</div>
          <div className="text-xs font-semibold text-slate-500 mt-1 uppercase tracking-wider">Total Tracked</div>
        </div>
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-3xl font-bold text-emerald-600">{onlineCount}</div>
          <div className="text-xs font-semibold text-slate-500 mt-1 uppercase tracking-wider">Live &amp; Active</div>
        </div>
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-3xl font-bold text-sky-600">{countriesCount}</div>
          <div className="text-xs font-semibold text-slate-500 mt-1 uppercase tracking-wider">Countries</div>
        </div>
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-3xl font-bold text-teal-600">{highAccuracyCount}</div>
          <div className="text-xs font-semibold text-slate-500 mt-1 uppercase tracking-wider">High Accuracy (&lt;25m)</div>
        </div>
      </div>

      {error && (
        <div className="mb-6 p-4 bg-rose-50 border border-rose-200 text-rose-800 rounded-xl text-xs font-medium shadow-sm">
          {error}
        </div>
      )}

      {/* Filter & Search Bar */}
      <div className="bg-white border border-slate-200/80 p-4 rounded-2xl mb-6 shadow-sm">
        <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-4">
          <div className="relative flex-1">
            <svg
              className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by user, device ID, city, country, or address..."
              className="w-full pl-10 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:border-purple-600 transition-all"
            />
          </div>

          <div className="flex items-center gap-2">
            {(['ALL', 'ONLINE', 'OFFLINE'] as const).map((filter) => (
              <button
                key={filter}
                onClick={() => setFilterFreshness(filter)}
                className={`px-3 py-2 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
                  filterFreshness === filter
                    ? 'bg-purple-600 text-white shadow-sm'
                    : 'bg-slate-50 text-slate-600 hover:bg-slate-100 border border-slate-200/80'
                }`}
              >
                {filter === 'ALL' ? 'All Devices' : filter === 'ONLINE' ? 'Active Now' : 'Older / Offline'}
              </button>
            ))}
          </div>
        </div>

        <div className="flex items-center justify-between text-xs text-slate-500 mt-3 pt-3 border-t border-slate-100">
          <div>
            Showing <span className="text-slate-800 font-semibold">{filtered.length}</span> of {locations.length} tracked locations
          </div>
          {search && (
            <button
              onClick={() => setSearch('')}
              className="text-purple-600 hover:text-purple-700 font-semibold hover:underline text-xs"
            >
              Clear Search
            </button>
          )}
        </div>
      </div>

      {/* Locations Table */}
      <div className="bg-white border border-slate-200/80 rounded-2xl overflow-hidden shadow-sm">
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b border-slate-200/80 bg-slate-50/80 text-slate-500 font-semibold uppercase tracking-wider">
              <th className="px-4 py-3.5">User &amp; Device</th>
              <th className="px-4 py-3.5">Region &amp; Country</th>
              <th className="px-4 py-3.5">Coordinates &amp; Accuracy</th>
              <th className="px-4 py-3.5">Last Sync</th>
              <th className="px-4 py-3.5 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <tr>
                <td colSpan={5} className="px-4 py-12 text-center text-slate-400">
                  <div className="flex items-center justify-center gap-2 font-medium">
                    <svg className="animate-spin w-4 h-4 text-purple-600" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                    Loading location telemetry…
                  </div>
                </td>
              </tr>
            ) : filtered.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-12 text-center text-slate-400 font-medium">
                  {locations.length === 0
                    ? 'No location telemetry recorded yet. Once mobile devices report location, they will appear here in real-time.'
                    : 'No device locations matching your search filter.'}
                </td>
              </tr>
            ) : (
              filtered.map((loc) => {
                const active = isFresh(loc.updatedAt);
                return (
                  <tr key={loc.id || loc.deviceId} className="hover:bg-slate-50/60 transition-colors">
                    {/* User & Device Info */}
                    <td className="px-4 py-3.5">
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-full bg-gradient-to-tr from-purple-600 to-indigo-600 flex items-center justify-center font-bold text-white text-xs shadow-sm flex-shrink-0">
                          {loc.userName.charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-slate-900 font-semibold">{loc.userName}</span>
                            <span
                              className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-bold ${
                                active
                                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                  : 'bg-slate-100 text-slate-500 border border-slate-200'
                              }`}
                            >
                              <span className={`w-1.5 h-1.5 rounded-full ${active ? 'bg-emerald-500 animate-pulse' : 'bg-slate-400'}`}></span>
                              {active ? 'ONLINE' : 'STANDBY'}
                            </span>
                          </div>
                          <div className="text-slate-500 font-mono text-[11px] mt-0.5">
                            ID: {loc.deviceId.substring(0, 14)}…
                          </div>
                        </div>
                      </div>
                    </td>

                    {/* Region & Country */}
                    <td className="px-4 py-3.5">
                      <div className="font-semibold text-slate-800">{loc.city}</div>
                      <div className="text-slate-500 text-[11px] font-medium">{loc.country}</div>
                    </td>

                    {/* Coordinates & Accuracy */}
                    <td className="px-4 py-3.5">
                      <div className="flex items-center gap-1.5">
                        <span className="font-mono text-slate-700 font-medium bg-slate-100 px-2 py-0.5 rounded text-[11px]">
                          {loc.latitude.toFixed(4)}, {loc.longitude.toFixed(4)}
                        </span>
                        <span className="text-slate-400 text-[10px] font-semibold">
                          (±{Math.round(loc.accuracy)}m)
                        </span>
                      </div>
                      <div className="text-slate-400 text-[11px] truncate max-w-xs mt-0.5">
                        {loc.formattedAddress}
                      </div>
                    </td>

                    {/* Last Sync */}
                    <td className="px-4 py-3.5 text-slate-600 font-medium">
                      {timeAgo(loc.updatedAt)}
                    </td>

                    {/* Action Button */}
                    <td className="px-4 py-3.5 text-right">
                      <button
                        onClick={() => setSelectedLocation(loc)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-purple-50 hover:bg-purple-100 text-purple-700 border border-purple-200 rounded-lg text-xs font-semibold transition-colors cursor-pointer"
                      >
                        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                        </svg>
                        Inspect Location
                      </button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Interactive Location Detail Modal */}
      {selectedLocation && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-xl w-full p-6 shadow-2xl border border-slate-100 animate-in fade-in zoom-in-95 duration-150">
            {/* Modal Header */}
            <div className="flex items-start justify-between gap-4 mb-4 pb-4 border-b border-slate-100">
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-lg font-bold text-slate-900">{selectedLocation.userName}</h2>
                  <span
                    className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold ${
                      isFresh(selectedLocation.updatedAt)
                        ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                        : 'bg-slate-100 text-slate-600 border border-slate-200'
                    }`}
                  >
                    <span className={`w-1.5 h-1.5 rounded-full ${isFresh(selectedLocation.updatedAt) ? 'bg-emerald-500 animate-pulse' : 'bg-slate-400'}`}></span>
                    {isFresh(selectedLocation.updatedAt) ? 'Active' : 'Offline'}
                  </span>
                </div>
                <div className="text-xs text-slate-500 font-mono mt-0.5">
                  Device: {selectedLocation.deviceId}
                </div>
              </div>
              <button
                onClick={() => setSelectedLocation(null)}
                className="p-1.5 rounded-xl text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* Metadata Grid */}
            <div className="grid grid-cols-2 gap-3 mb-4 text-xs">
              <div className="p-3 bg-slate-50 rounded-xl border border-slate-100">
                <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Region</div>
                <div className="text-slate-900 font-bold mt-0.5">
                  {selectedLocation.city}, {selectedLocation.country}
                </div>
              </div>

              <div className="p-3 bg-slate-50 rounded-xl border border-slate-100">
                <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Last Recorded</div>
                <div className="text-slate-900 font-bold mt-0.5">
                  {timeAgo(selectedLocation.updatedAt)} ({new Date(selectedLocation.updatedAt).toLocaleTimeString()})
                </div>
              </div>
            </div>

            {/* Coordinates & Accuracy Bar */}
            <div className="p-3 bg-purple-50/60 rounded-xl border border-purple-100 flex items-center justify-between mb-4">
              <div>
                <div className="text-[10px] font-bold text-purple-700 uppercase tracking-wider">GPS Coordinates</div>
                <div className="font-mono text-xs font-bold text-slate-900 mt-0.5">
                  {selectedLocation.latitude.toFixed(6)}, {selectedLocation.longitude.toFixed(6)}
                  <span className="text-purple-600 font-medium ml-2">(±{Math.round(selectedLocation.accuracy)}m)</span>
                </div>
              </div>

              <button
                onClick={() => copyCoordinates(selectedLocation.latitude, selectedLocation.longitude)}
                className="px-2.5 py-1.5 bg-white hover:bg-purple-100 text-purple-700 border border-purple-200 rounded-lg text-xs font-semibold shadow-xs transition-colors cursor-pointer"
              >
                {copiedCoords ? '✓ Copied' : 'Copy'}
              </button>
            </div>

            {/* Formatted Full Address */}
            <div className="mb-4">
              <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-1">
                Resolved Street Address
              </div>
              <p className="text-xs text-slate-700 bg-slate-50 p-3 rounded-xl border border-slate-100 leading-relaxed">
                {selectedLocation.formattedAddress}
              </p>
            </div>

            {/* OpenStreetMap Interactive Map Display */}
            <div className="mb-5 overflow-hidden rounded-xl border border-slate-200 shadow-inner">
              <iframe
                title="Device Geolocation Map"
                width="100%"
                height="260"
                frameBorder="0"
                scrolling="no"
                src={`https://www.openstreetmap.org/export/embed.html?bbox=${selectedLocation.longitude - 0.005}%2C${selectedLocation.latitude - 0.005}%2C${selectedLocation.longitude + 0.005}%2C${selectedLocation.latitude + 0.005}&layer=mapnik&marker=${selectedLocation.latitude}%2C${selectedLocation.longitude}`}
                className="w-full block"
              ></iframe>
            </div>

            {/* Modal Actions */}
            <div className="flex items-center justify-between pt-2 border-t border-slate-100">
              <a
                href={`https://www.google.com/maps?q=${selectedLocation.latitude},${selectedLocation.longitude}`}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-1.5 px-3.5 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-xl font-semibold text-xs shadow-sm transition-colors cursor-pointer"
              >
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                </svg>
                Open in Google Maps
              </a>

              <button
                onClick={() => setSelectedLocation(null)}
                className="px-4 py-2 border border-slate-200 rounded-xl text-slate-600 hover:bg-slate-50 font-semibold text-xs transition-colors cursor-pointer"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
