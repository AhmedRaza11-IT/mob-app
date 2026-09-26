'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import dynamic from 'next/dynamic';
import type { LocationPoint } from '@/components/PathTracerMap';

const PathTracerMap = dynamic(() => import('@/components/PathTracerMap'), {
  ssr: false,
  loading: () => (
    <div className="h-72 w-full rounded-xl bg-slate-100 animate-pulse flex flex-col items-center justify-center text-xs text-slate-400 gap-2 border border-slate-200">
      <div className="w-5 h-5 border-2 border-purple-500 border-t-transparent rounded-full animate-spin"></div>
      <span>Loading route tracer map...</span>
    </div>
  ),
});

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

type TimeframeOption = '1m' | '2m' | '5m' | '10m' | '20m' | '30m' | '1h' | '3h' | '6h' | '24h' | 'all';

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
  checkpointCount?: number;
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

function calculateDistanceMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371e3; // metres
  const φ1 = (lat1 * Math.PI) / 180;
  const φ2 = (lat2 * Math.PI) / 180;
  const Δφ = ((lat2 - lat1) * Math.PI) / 180;
  const Δλ = ((lon2 - lon1) * Math.PI) / 180;

  const a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
            Math.cos(φ1) * Math.cos(φ2) *
            Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return Math.round(R * c);
}

function formatDistance(meters: number): string {
  if (meters < 1000) return `${meters} m`;
  return `${(meters / 1000).toFixed(2)} km`;
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
  const [activeTab, setActiveTab] = useState<'MAP' | 'WAYPOINTS'>('MAP');
  const [copiedWpId, setCopiedWpId] = useState<string | null>(null);

  // Historical Path Tracing State
  const [timeframe, setTimeframe] = useState<TimeframeOption>('1h');
  const [historyPoints, setHistoryPoints] = useState<LocationPoint[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  const handleTimeframeChange = (newTf: TimeframeOption) => {
    setTimeframe(newTf);
  };

  const fetchHistory = useCallback(async (loc: DeviceLocation, tf: TimeframeOption) => {
    setLoadingHistory(true);
    try {
      const params = new URLSearchParams();
      if (loc.userId) params.set('userId', loc.userId);
      if (loc.deviceId) params.set('deviceId', loc.deviceId);
      params.set('timeframe', tf);

      const res = await fetch(`/api/devices/location/history?${params.toString()}`);
      if (res.ok) {
        const data = await res.json();
        setHistoryPoints(data.points || []);
      } else {
        setHistoryPoints([{
          id: loc.id,
          latitude: loc.latitude,
          longitude: loc.longitude,
          accuracy: loc.accuracy,
          formattedAddress: loc.formattedAddress,
          city: loc.city,
          country: loc.country,
          timestamp: loc.updatedAt,
        }]);
      }
    } catch {
      setHistoryPoints([{
        id: loc.id,
        latitude: loc.latitude,
        longitude: loc.longitude,
        accuracy: loc.accuracy,
        formattedAddress: loc.formattedAddress,
        city: loc.city,
        country: loc.country,
        timestamp: loc.updatedAt,
      }]);
    } finally {
      setLoadingHistory(false);
    }
  }, []);

  useEffect(() => {
    if (selectedLocation) {
      fetchHistory(selectedLocation, timeframe);
    }
  }, [selectedLocation, timeframe, fetchHistory]);

  const mapsDirectionsUrl = useMemo(() => {
    if (!historyPoints || historyPoints.length === 0) {
      return selectedLocation ? `https://www.google.com/maps?q=${selectedLocation.latitude},${selectedLocation.longitude}` : '#';
    }
    if (historyPoints.length === 1) {
      return `https://www.google.com/maps?q=${historyPoints[0].latitude},${historyPoints[0].longitude}`;
    }
    const origin = `${historyPoints[0].latitude},${historyPoints[0].longitude}`;
    const destination = `${historyPoints[historyPoints.length - 1].latitude},${historyPoints[historyPoints.length - 1].longitude}`;
    return `https://www.google.com/maps/dir/?api=1&origin=${origin}&destination=${destination}`;
  }, [historyPoints, selectedLocation]);

  const totalDistance = useMemo(() => {
    if (!historyPoints || historyPoints.length < 2) return 0;
    let total = 0;
    for (let i = 1; i < historyPoints.length; i++) {
      total += calculateDistanceMeters(
        historyPoints[i - 1].latitude,
        historyPoints[i - 1].longitude,
        historyPoints[i].latitude,
        historyPoints[i].longitude
      );
    }
    return total;
  }, [historyPoints]);

  const copyWaypointCoords = (id: string, lat: number, lon: number) => {
    navigator.clipboard.writeText(`${lat}, ${lon}`);
    setCopiedWpId(id);
    setTimeout(() => setCopiedWpId(null), 1500);
  };

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
              const existingIndex = prev.findIndex(
                (l) => (l.userId && msg.userId && l.userId.toLowerCase() === msg.userId.toLowerCase()) || 
                       (l.userName && msg.userName && l.userName.toLowerCase() === msg.userName.toLowerCase()) ||
                       l.deviceId === msg.deviceId
              );
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
                checkpointCount: msg.checkpointCount || (existingIndex >= 0 ? (prev[existingIndex].checkpointCount || 1) + 1 : 1),
              };

              if (existingIndex >= 0) {
                const next = [...prev];
                next[existingIndex] = updatedItem;
                return next;
              }
              return [updatedItem, ...prev];
            });

            // If currently viewing this device, append to historical path in real-time
            setHistoryPoints((prevPts) => {
              return [...prevPts, {
                id: msg.id || `pt_${Date.now()}`,
                latitude: Number(msg.latitude),
                longitude: Number(msg.longitude),
                accuracy: Number(msg.accuracy || 0),
                formattedAddress: msg.formattedAddress,
                city: msg.city,
                country: msg.country,
                timestamp: msg.updatedAt,
              }];
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
            Showing <span className="text-slate-800 font-semibold">{filtered.length}</span> of {locations.length} tracked users
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
      <div className="bg-white border border-slate-200/80 rounded-2xl overflow-x-auto shadow-sm">
        <table className="w-full text-left text-xs min-w-[840px]">
          <thead>
            <tr className="border-b border-slate-200/80 bg-slate-50/80 text-slate-500 font-semibold uppercase tracking-wider whitespace-nowrap">
              <th className="px-5 py-3.5 min-w-[240px]">User &amp; Device</th>
              <th className="px-5 py-3.5 min-w-[150px]">Region &amp; Country</th>
              <th className="px-5 py-3.5 min-w-[260px]">Coordinates &amp; Accuracy</th>
              <th className="px-5 py-3.5 min-w-[110px]">Last Sync</th>
              <th className="px-5 py-3.5 text-right min-w-[160px]">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <tr>
                <td colSpan={5} className="px-5 py-12 text-center text-slate-400">
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
                <td colSpan={5} className="px-5 py-12 text-center text-slate-400 font-medium">
                  {locations.length === 0
                    ? 'No location telemetry recorded yet. Once mobile devices report location, they will appear here in real-time.'
                    : 'No device locations matching your search filter.'}
                </td>
              </tr>
            ) : (
              filtered.map((loc) => {
                const active = isFresh(loc.updatedAt);
                return (
                  <tr key={loc.userId || loc.deviceId} className="hover:bg-slate-50/60 transition-colors">
                    {/* User & Device Info */}
                    <td className="px-5 py-3.5 whitespace-nowrap">
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-full bg-gradient-to-tr from-purple-600 to-indigo-600 flex items-center justify-center font-bold text-white text-xs shadow-sm flex-shrink-0">
                          {loc.userName.charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-slate-900 font-semibold whitespace-nowrap">{loc.userName}</span>
                            <span
                              className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-bold flex-shrink-0 ${
                                active
                                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                  : 'bg-slate-100 text-slate-500 border border-slate-200'
                              }`}
                            >
                              <span className={`w-1.5 h-1.5 rounded-full ${active ? 'bg-emerald-500 animate-pulse' : 'bg-slate-400'}`}></span>
                              {active ? 'ONLINE' : 'STANDBY'}
                            </span>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                setActiveTab('WAYPOINTS');
                                setSelectedLocation(loc);
                              }}
                              className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-purple-50 hover:bg-purple-100 text-purple-700 hover:text-purple-900 border border-purple-200 hover:border-purple-300 transition-all cursor-pointer shadow-2xs flex-shrink-0 whitespace-nowrap"
                              title="Click to view all waypoints"
                            >
                              <svg className="w-2.5 h-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                              </svg>
                              {loc.checkpointCount || 1} {(loc.checkpointCount || 1) === 1 ? 'waypoint' : 'waypoints'}
                            </button>
                          </div>
                          <div className="text-slate-500 font-mono text-[11px] mt-0.5 whitespace-nowrap">
                            ID: {loc.deviceId.substring(0, 14)}…
                          </div>
                        </div>
                      </div>
                    </td>

                    {/* Region & Country */}
                    <td className="px-5 py-3.5 whitespace-nowrap">
                      <div className="font-semibold text-slate-800">{loc.city}</div>
                      <div className="text-slate-500 text-[11px] font-medium">{loc.country}</div>
                    </td>

                    {/* Coordinates & Accuracy */}
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-1.5 whitespace-nowrap">
                        <span className="font-mono text-slate-700 font-medium bg-slate-100 px-2 py-0.5 rounded text-[11px]">
                          {loc.latitude.toFixed(4)}, {loc.longitude.toFixed(4)}
                        </span>
                        <span className="text-slate-400 text-[10px] font-semibold">
                          (±{Math.round(loc.accuracy)}m)
                        </span>
                      </div>
                      <div className="text-slate-500 text-[11px] truncate max-w-xs mt-0.5 block" title={loc.formattedAddress}>
                        {loc.formattedAddress}
                      </div>
                    </td>

                    {/* Last Sync */}
                    <td className="px-5 py-3.5 text-slate-600 font-medium whitespace-nowrap">
                      {timeAgo(loc.updatedAt)}
                    </td>

                    {/* Action Button */}
                    <td className="px-5 py-3.5 text-right whitespace-nowrap">
                      <button
                        onClick={() => {
                          setActiveTab('MAP');
                          setSelectedLocation(loc);
                        }}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-purple-50 hover:bg-purple-100 text-purple-700 border border-purple-200 rounded-lg text-xs font-semibold whitespace-nowrap transition-colors cursor-pointer flex-shrink-0"
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

      {/* Interactive Location Detail & Waypoints Modal */}
      {selectedLocation && (
        <div 
          onClick={(e) => { if (e.target === e.currentTarget) setSelectedLocation(null); }}
          className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-2 sm:p-4 md:p-6 overflow-hidden"
        >
          <div className="bg-white rounded-2xl max-w-3xl lg:max-w-4xl w-full max-h-[92vh] flex flex-col shadow-2xl border border-slate-200 overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            {/* Fixed Modal Header */}
            <div className="flex items-center justify-between gap-4 px-5 py-3.5 border-b border-slate-100 bg-white flex-shrink-0">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-gradient-to-tr from-purple-600 to-indigo-600 flex items-center justify-center font-bold text-white text-sm shadow-sm flex-shrink-0">
                  {selectedLocation.userName.charAt(0).toUpperCase()}
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <h2 className="text-base font-bold text-slate-900">{selectedLocation.userName}</h2>
                    <span
                      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold ${
                        isFresh(selectedLocation.updatedAt)
                          ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                          : 'bg-slate-100 text-slate-600 border border-slate-200'
                      }`}
                    >
                      <span className={`w-1.5 h-1.5 rounded-full ${isFresh(selectedLocation.updatedAt) ? 'bg-emerald-500 animate-pulse' : 'bg-slate-400'}`}></span>
                      {isFresh(selectedLocation.updatedAt) ? 'Live & Active' : 'Offline'}
                    </span>
                    <button
                      onClick={() => setActiveTab('WAYPOINTS')}
                      className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-purple-50 hover:bg-purple-100 text-purple-700 border border-purple-200 transition-colors cursor-pointer"
                      title="Click to view all waypoints"
                    >
                      <svg className="w-2.5 h-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                      </svg>
                      {historyPoints.length} {historyPoints.length === 1 ? 'waypoint' : 'waypoints'}
                    </button>
                  </div>
                  <div className="text-[11px] text-slate-400 font-mono mt-0.5">
                    Device ID: {selectedLocation.deviceId}
                  </div>
                </div>
              </div>

              <button
                onClick={() => setSelectedLocation(null)}
                className="p-1.5 rounded-xl text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
                title="Close Modal (Esc)"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* Navigation Tabs Bar */}
            <div className="flex items-center gap-1 px-5 pt-2 border-b border-slate-100 bg-slate-50/80 flex-shrink-0">
              <button
                onClick={() => setActiveTab('MAP')}
                className={`flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-t-lg border-b-2 transition-all cursor-pointer ${
                  activeTab === 'MAP'
                    ? 'border-purple-600 text-purple-700 bg-white shadow-xs font-bold'
                    : 'border-transparent text-slate-500 hover:text-slate-800 hover:bg-white/60'
                }`}
              >
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
                </svg>
                Route Map &amp; Tracer
              </button>

              <button
                onClick={() => setActiveTab('WAYPOINTS')}
                className={`flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-t-lg border-b-2 transition-all cursor-pointer ${
                  activeTab === 'WAYPOINTS'
                    ? 'border-purple-600 text-purple-700 bg-white shadow-xs font-bold'
                    : 'border-transparent text-slate-500 hover:text-slate-800 hover:bg-white/60'
                }`}
              >
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                </svg>
                All Waypoints ({historyPoints.length})
              </button>
            </div>

            {/* Scrollable Modal Content */}
            <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-3.5 min-h-0">
              {activeTab === 'MAP' ? (
                /* TAB 1: ROUTE MAP & TRACER */
                <>
                  {/* Two Wide Summary Cards */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                    <div className="p-3 bg-slate-50/90 rounded-xl border border-slate-200/80 flex flex-col justify-between">
                      <div className="flex items-center justify-between">
                        <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Region &amp; Timing</span>
                        <span className="text-[10px] text-slate-500 font-semibold bg-slate-100 px-2 py-0.5 rounded-full">
                          {timeAgo(selectedLocation.updatedAt)}
                        </span>
                      </div>
                      <div className="text-slate-900 font-bold text-sm mt-1" dir="auto">
                        {selectedLocation.city}, {selectedLocation.country}
                      </div>
                      <div className="text-[11px] text-slate-400 mt-1 font-mono">
                        Ping: {new Date(selectedLocation.updatedAt).toLocaleString()}
                      </div>
                    </div>

                    <div className="p-3 bg-purple-50/60 rounded-xl border border-purple-100 flex flex-col justify-between">
                      <div className="flex items-center justify-between">
                        <span className="text-[10px] font-bold text-purple-700 uppercase tracking-wider">GPS Coordinates</span>
                        <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-white text-purple-700 border border-purple-200">
                          ±{Math.round(selectedLocation.accuracy)}m precision
                        </span>
                      </div>
                      <div className="flex items-center justify-between mt-1">
                        <div className="font-mono text-xs font-bold text-slate-900">
                          {selectedLocation.latitude.toFixed(6)}, {selectedLocation.longitude.toFixed(6)}
                        </div>
                        <div className="flex items-center gap-1.5">
                          <button
                            onClick={() => copyCoordinates(selectedLocation.latitude, selectedLocation.longitude)}
                            className="px-2 py-1 bg-white hover:bg-purple-100 text-purple-700 border border-purple-200 rounded-lg text-[11px] font-semibold transition-colors cursor-pointer"
                          >
                            {copiedCoords ? '✓ Copied' : 'Copy'}
                          </button>
                          <a
                            href={`https://www.google.com/maps?q=${selectedLocation.latitude},${selectedLocation.longitude}`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="px-2 py-1 bg-white hover:bg-slate-100 text-slate-700 border border-slate-200 rounded-lg text-[11px] font-semibold transition-colors inline-flex items-center gap-1"
                          >
                            Maps
                            <svg className="w-2.5 h-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                            </svg>
                          </a>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Full-Width Resolved Street Address */}
                  <div className="w-full p-3.5 bg-slate-50/90 rounded-xl border border-slate-200/80">
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                        Resolved Street Address
                      </span>
                      <span className="text-[10px] text-slate-400 font-medium">
                        Reverse Geocoded (OpenStreetMap)
                      </span>
                    </div>
                    <div dir="auto" className="text-slate-800 text-xs sm:text-sm font-medium leading-relaxed break-words text-start">
                      {selectedLocation.formattedAddress || 'No address text resolved'}
                    </div>
                  </div>

                  {/* Timeframe Filter Bar & Path Tracer Header */}
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 bg-slate-50 p-2.5 rounded-xl border border-slate-100">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-bold text-slate-800">Route &amp; Waypoints Tracer</span>
                      <button
                        onClick={() => setActiveTab('WAYPOINTS')}
                        className="text-[11px] font-semibold text-purple-700 bg-purple-50 hover:bg-purple-100 border border-purple-200 px-2 py-0.5 rounded-full transition-colors cursor-pointer"
                        title="Click to view all waypoints list"
                      >
                        {loadingHistory
                          ? 'Fetching waypoints...'
                          : `${historyPoints.length} checkpoint${historyPoints.length === 1 ? '' : 's'} recorded (view all)`}
                      </button>
                    </div>

                    {/* Dropdown Selector */}
                    <div className="flex items-center gap-2">
                      <label htmlFor="timeframe-select" className="text-xs font-semibold text-slate-500 whitespace-nowrap">
                        View Window:
                      </label>
                      <div className="relative">
                        <select
                          id="timeframe-select"
                          value={timeframe}
                          onChange={(e) => handleTimeframeChange(e.target.value as TimeframeOption)}
                          className="appearance-none bg-white hover:bg-slate-50 text-slate-800 text-xs font-semibold py-1.5 pl-3 pr-8 rounded-xl border border-slate-200 shadow-xs focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500 transition-colors cursor-pointer"
                        >
                          <option value="1m">1 Minute (1M)</option>
                          <option value="2m">2 Minutes (2M)</option>
                          <option value="5m">5 Minutes (5M)</option>
                          <option value="10m">10 Minutes (10M)</option>
                          <option value="20m">20 Minutes (20M)</option>
                          <option value="30m">30 Minutes (30M)</option>
                          <option value="1h">1 Hour (1H)</option>
                          <option value="3h">3 Hours (3H)</option>
                          <option value="6h">6 Hours (6H)</option>
                          <option value="24h">24 Hours (24H)</option>
                          <option value="all">All Recorded History</option>
                        </select>
                        <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center px-2.5 text-slate-400">
                          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                          </svg>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Leaflet Dynamic Route Path Tracer Map */}
                  <div className="w-full rounded-xl overflow-hidden border border-slate-200 shadow-xs">
                    <PathTracerMap 
                      points={historyPoints} 
                      onViewWaypoints={() => setActiveTab('WAYPOINTS')}
                    />
                  </div>
                </>
              ) : (
                /* TAB 2: ALL WAYPOINTS DETAILED LIST */
                <div className="space-y-3">
                  {/* Waypoints Header Summary Bar */}
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-3 bg-gradient-to-r from-purple-50/70 to-indigo-50/70 rounded-xl border border-purple-100">
                    <div className="flex items-center gap-3">
                      <div className="w-9 h-9 rounded-xl bg-purple-600 text-white flex items-center justify-center font-bold text-sm shadow-xs">
                        {historyPoints.length}
                      </div>
                      <div>
                        <div className="text-xs font-bold text-slate-900">
                          All Recorded Waypoints
                        </div>
                        <div className="text-[11px] text-slate-500">
                          Total Traveled Distance: <span className="font-semibold text-purple-700">{formatDistance(totalDistance)}</span>
                        </div>
                      </div>
                    </div>

                    {/* Timeframe Selector in Waypoints View */}
                    <div className="flex items-center gap-2">
                      <label htmlFor="timeframe-select-wp" className="text-xs font-semibold text-slate-500 whitespace-nowrap">
                        Timeframe:
                      </label>
                      <div className="relative">
                        <select
                          id="timeframe-select-wp"
                          value={timeframe}
                          onChange={(e) => handleTimeframeChange(e.target.value as TimeframeOption)}
                          className="appearance-none bg-white hover:bg-slate-50 text-slate-800 text-xs font-semibold py-1.5 pl-3 pr-8 rounded-xl border border-slate-200 shadow-xs focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500 transition-colors cursor-pointer"
                        >
                          <option value="1m">1 Minute (1M)</option>
                          <option value="2m">2 Minutes (2M)</option>
                          <option value="5m">5 Minutes (5M)</option>
                          <option value="10m">10 Minutes (10M)</option>
                          <option value="20m">20 Minutes (20M)</option>
                          <option value="30m">30 Minutes (30M)</option>
                          <option value="1h">1 Hour (1H)</option>
                          <option value="3h">3 Hours (3H)</option>
                          <option value="6h">6 Hours (6H)</option>
                          <option value="24h">24 Hours (24H)</option>
                          <option value="all">All Recorded History</option>
                        </select>
                        <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center px-2.5 text-slate-400">
                          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                          </svg>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Waypoints List */}
                  {loadingHistory ? (
                    <div className="p-8 text-center text-xs text-slate-400 flex items-center justify-center gap-2">
                      <svg className="animate-spin w-4 h-4 text-purple-600" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                      </svg>
                      Loading historical waypoints...
                    </div>
                  ) : historyPoints.length === 0 ? (
                    <div className="p-8 text-center bg-slate-50 border border-dashed border-slate-200 rounded-xl text-xs text-slate-500">
                      No waypoints found for this timeframe. Select a wider timeframe (e.g. 6 Hours or All) to view earlier waypoints.
                    </div>
                  ) : (
                    <div className="space-y-2.5">
                      {/* Render in reverse chronological order (latest on top) */}
                      {[...historyPoints].reverse().map((wp, revIdx) => {
                        const originalIndex = historyPoints.length - 1 - revIdx;
                        const isLatest = originalIndex === historyPoints.length - 1;
                        const isStart = originalIndex === 0;

                        // Calculate distance from previous point
                        let legDistance = 0;
                        if (originalIndex > 0) {
                          const prevWp = historyPoints[originalIndex - 1];
                          legDistance = calculateDistanceMeters(prevWp.latitude, prevWp.longitude, wp.latitude, wp.longitude);
                        }

                        return (
                          <div
                            key={wp.id || `wp-${originalIndex}`}
                            className={`p-3.5 rounded-xl border transition-all ${
                              isLatest
                                ? 'bg-purple-50/40 border-purple-200 shadow-2xs'
                                : 'bg-white border-slate-200 hover:border-slate-300'
                            }`}
                          >
                            {/* Header row: Waypoint badge, status tags, time, and action buttons */}
                            <div className="flex items-center justify-between gap-2 flex-wrap">
                              <div className="flex items-center gap-2 flex-wrap">
                                <div
                                  className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold shadow-xs shrink-0 ${
                                    isLatest
                                      ? 'bg-purple-600 text-white'
                                      : isStart
                                      ? 'bg-emerald-600 text-white'
                                      : 'bg-slate-200 text-slate-700'
                                  }`}
                                >
                                  {originalIndex + 1}
                                </div>
                                <span className="text-xs font-bold text-slate-900">
                                  Waypoint #{originalIndex + 1}
                                </span>
                                {isLatest && (
                                  <span className="px-1.5 py-0.5 bg-purple-100 text-purple-700 border border-purple-200 rounded text-[10px] font-bold">
                                    LATEST POSITION
                                  </span>
                                )}
                                {isStart && (
                                  <span className="px-1.5 py-0.5 bg-emerald-100 text-emerald-700 border border-emerald-200 rounded text-[10px] font-bold">
                                    START / DEPARTURE
                                  </span>
                                )}
                                <span className="text-[11px] text-slate-500 font-medium">
                                  {timeAgo(wp.timestamp)}
                                </span>
                                <span className="text-[10px] text-slate-400 font-mono">
                                  ({new Date(wp.timestamp).toLocaleTimeString()})
                                </span>
                              </div>

                              <div className="flex items-center gap-1.5 shrink-0 ml-auto">
                                <button
                                  onClick={() => setActiveTab('MAP')}
                                  className="px-2.5 py-1 text-[11px] font-semibold text-purple-700 bg-purple-50 hover:bg-purple-100 border border-purple-200 rounded-lg transition-colors cursor-pointer"
                                >
                                  View on Map
                                </button>
                                <a
                                  href={`https://www.google.com/maps?q=${wp.latitude},${wp.longitude}`}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="px-2.5 py-1 text-[11px] font-semibold text-slate-600 bg-slate-100 hover:bg-slate-200 border border-slate-200 rounded-lg transition-colors cursor-pointer inline-flex items-center gap-1"
                                >
                                  Google Maps
                                  <svg className="w-2.5 h-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                                  </svg>
                                </a>
                              </div>
                            </div>

                            {/* Full width address container - NEVER squeezed, text-start, dir="auto", leading-relaxed */}
                            <div className="w-full mt-2.5 py-2 px-3 bg-slate-50/90 rounded-lg border border-slate-100">
                              <div dir="auto" className="text-xs text-slate-800 font-medium leading-relaxed break-words text-start">
                                {wp.formattedAddress || 'No address text available'}
                              </div>
                            </div>

                            {/* Coordinates & stats bar */}
                            <div className="flex items-center gap-3 mt-2 text-[11px] text-slate-500 flex-wrap">
                              <div className="flex items-center gap-1 font-mono text-slate-700 bg-slate-100 px-2 py-0.5 rounded">
                                <span>{wp.latitude.toFixed(6)}, {wp.longitude.toFixed(6)}</span>
                                <button
                                  onClick={() => copyWaypointCoords(wp.id, wp.latitude, wp.longitude)}
                                  className="text-purple-600 hover:text-purple-800 font-bold ml-1 cursor-pointer"
                                  title="Copy Coordinates"
                                >
                                  {copiedWpId === wp.id ? '✓' : 'Copy'}
                                </button>
                              </div>

                              <span className="text-slate-400 font-semibold">
                                ±{Math.round(wp.accuracy)}m accuracy
                              </span>

                              {originalIndex > 0 && (
                                <span className="text-purple-600 font-semibold">
                                  +{formatDistance(legDistance)} from W#{originalIndex}
                                </span>
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Fixed Modal Footer */}
            <div className="px-5 py-3 border-t border-slate-100 bg-slate-50/90 flex items-center justify-between flex-shrink-0">
              <a
                href={mapsDirectionsUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-1.5 px-3.5 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-xl font-semibold text-xs shadow-sm transition-colors cursor-pointer"
              >
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
                </svg>
                {historyPoints.length > 1 ? 'Directions in Google Maps' : 'Open in Google Maps'}
              </a>

              <button
                onClick={() => setSelectedLocation(null)}
                className="px-4 py-2 border border-slate-200 rounded-xl text-slate-700 bg-white hover:bg-slate-100 font-semibold text-xs shadow-xs transition-colors cursor-pointer"
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
