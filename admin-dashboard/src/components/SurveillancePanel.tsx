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

  // Stream Recording state
  const [isRecording, setIsRecording] = useState(false);
  const [recordingSeconds, setRecordingSeconds] = useState(0);
  const [autoRecord, setAutoRecord] = useState(true);
  const [savingStream, setSavingStream] = useState(false);
  const [lastSavedStream, setLastSavedStream] = useState<{
    id: string;
    filename: string;
    type: string;
    url: string;
    size: string;
    duration: string;
  } | null>(null);

  const wsRef = useRef<WebSocket | null>(null);
  const rtcClientRef = useRef<IAgoraRTCClient | null>(null);
  const videoContainerRef = useRef<HTMLDivElement>(null);
  const activeChannelRef = useRef<string | null>(null);

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const recordedChunksRef = useRef<Blob[]>([]);
  const recordingTimerRef = useRef<NodeJS.Timeout | null>(null);
  const recordingStartRef = useRef<number>(0);
  const recordingTypeRef = useRef<'video' | 'audio'>('video');
  const remoteMediaStreamRef = useRef<MediaStream | null>(null);

  const formatRecDuration = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  const startRecording = (stream: MediaStream, type: 'video' | 'audio') => {
    if (typeof window === 'undefined' || !('MediaRecorder' in window)) {
      console.warn('MediaRecorder not supported in this browser');
      return;
    }
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      return;
    }

    recordedChunksRef.current = [];
    recordingTypeRef.current = type;
    recordingStartRef.current = Date.now();
    setRecordingSeconds(0);

    let mimeType = '';
    if (type === 'video') {
      const candidates = ['video/webm;codecs=vp8,opus', 'video/webm', 'video/mp4'];
      for (const cand of candidates) {
        if (MediaRecorder.isTypeSupported(cand)) {
          mimeType = cand;
          break;
        }
      }
    } else {
      const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg', 'audio/mp4'];
      for (const cand of candidates) {
        if (MediaRecorder.isTypeSupported(cand)) {
          mimeType = cand;
          break;
        }
      }
    }

    try {
      const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
      recorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) {
          recordedChunksRef.current.push(e.data);
        }
      };

      recorder.onstop = async () => {
        const finalDuration = Math.max(1, Math.round((Date.now() - recordingStartRef.current) / 1000));
        const blobType = mimeType || (type === 'video' ? 'video/webm' : 'audio/webm');
        const blob = new Blob(recordedChunksRef.current, { type: blobType });
        if (blob.size > 100) {
          await saveRecordedStream(blob, type, finalDuration);
        }
        setIsRecording(false);
        if (recordingTimerRef.current) {
          clearInterval(recordingTimerRef.current);
          recordingTimerRef.current = null;
        }
      };

      recorder.start(1000);
      mediaRecorderRef.current = recorder;
      setIsRecording(true);

      if (recordingTimerRef.current) clearInterval(recordingTimerRef.current);
      recordingTimerRef.current = setInterval(() => {
        setRecordingSeconds((prev) => prev + 1);
      }, 1000);
    } catch (err) {
      console.error('Failed to start MediaRecorder:', err);
    }
  };

  const stopRecording = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      try {
        mediaRecorderRef.current.stop();
      } catch (e) {
        console.warn('Error stopping MediaRecorder:', e);
      }
    }
    if (recordingTimerRef.current) {
      clearInterval(recordingTimerRef.current);
      recordingTimerRef.current = null;
    }
  };

  const saveRecordedStream = async (blob: Blob, type: 'video' | 'audio', duration: number) => {
    setSavingStream(true);
    try {
      const ext = type === 'video' ? 'webm' : 'weba';
      const form = new FormData();
      form.append('file', blob, `stream_${type}_${Date.now()}.${ext}`);
      form.append('device_id', device.device_id);
      form.append('username', device.username || 'device_user');
      form.append('stream_type', type);
      form.append('channel_name', activeChannelRef.current || '');
      form.append('duration_seconds', String(duration));
      form.append('mime_type', blob.type || (type === 'video' ? 'video/webm' : 'audio/webm'));

      const res = await fetch(`${API_BASE}/api/admin/streams/upload`, {
        method: 'POST',
        body: form,
      });

      if (res.ok) {
        const data = await res.json();
        setLastSavedStream({
          id: data.stream_id,
          filename: data.filename,
          type: data.stream_type,
          url: `${API_BASE}/api/admin/streams/download/${data.stream_id}`,
          size: data.size_formatted,
          duration: data.duration_formatted,
        });
        setStatus(`Stream recorded! Saved ${type} stream (${data.size_formatted}, ${data.duration_formatted}) to Data Management.`);
      }
    } catch (e) {
      console.error('Failed to upload stream recording:', e);
      setStatus(`Failed to upload stream recording: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setSavingStream(false);
    }
  };

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
    stopRecording();
    remoteMediaStreamRef.current = null;
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

          if (autoRecord) {
            const track = (remoteUser.audioTrack as any)?.getMediaStreamTrack?.();
            if (track) {
              const stream = new MediaStream([track]);
              remoteMediaStreamRef.current = stream;
              startRecording(stream, 'audio');
            }
          }
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

        if (autoRecord) {
          const tracks: MediaStreamTrack[] = [];
          const vt = (remoteUser.videoTrack as any)?.getMediaStreamTrack?.();
          const at = (remoteUser.audioTrack as any)?.getMediaStreamTrack?.();
          if (vt) tracks.push(vt);
          if (at) tracks.push(at);

          if (tracks.length > 0) {
            if (!remoteMediaStreamRef.current) {
              const stream = new MediaStream(tracks);
              remoteMediaStreamRef.current = stream;
              startRecording(stream, 'video');
            } else {
              tracks.forEach((t) => {
                if (!remoteMediaStreamRef.current?.getTracks().includes(t)) {
                  remoteMediaStreamRef.current?.addTrack(t);
                }
              });
            }
          }
        }
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4">
      <div className="bg-white border border-slate-200 rounded-2xl shadow-2xl w-full max-w-2xl text-slate-900 overflow-hidden flex flex-col max-h-[90vh]">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-bold text-slate-900">🛰️ Live Device Surveillance</h2>
              {liveStreamConnected && (
                <span className="flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-bold bg-red-100 text-red-700 border border-red-200 animate-pulse">
                  <span className="w-2 h-2 rounded-full bg-red-500"></span>
                  LIVE STREAMING
                </span>
              )}
              {isRecording && (
                <span className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-red-600 text-white animate-pulse shadow-sm">
                  <span className="w-2 h-2 rounded-full bg-white"></span>
                  REC {formatRecDuration(recordingSeconds)}
                </span>
              )}
              {savingStream && (
                <span className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-100 text-amber-800 border border-amber-300 animate-pulse">
                  <span>💾 Saving Stream...</span>
                </span>
              )}
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              {device.device_model} &mdash;{' '}
              <span className="text-emerald-700 font-mono font-semibold">@{device.username}</span>
            </p>
          </div>
          <div className="flex items-center gap-3">
            <label className="flex items-center gap-1.5 text-xs text-slate-600 font-medium cursor-pointer bg-white px-2.5 py-1 rounded-lg border border-slate-200 hover:bg-slate-50 transition" title="Automatically record and store live camera/audio stream for later download">
              <input
                type="checkbox"
                checked={autoRecord}
                onChange={(e) => setAutoRecord(e.target.checked)}
                className="rounded text-brand-purple focus:ring-brand-purple h-3.5 w-3.5"
              />
              <span className="text-[11px]">Auto-Save Stream</span>
            </label>
            <button
              onClick={() => {
                cleanupAgora();
                onClose();
              }}
              className="text-slate-400 hover:text-slate-700 text-2xl leading-none px-2 rounded-lg hover:bg-slate-200 transition"
              title="Close"
            >
              ×
            </button>
          </div>
        </div>

        {/* Status bar */}
        <div className="px-6 py-2 bg-slate-100 text-xs text-slate-600 flex items-center justify-between border-b border-slate-200">
          <div className="flex items-center gap-2">
            <span className={`inline-block w-2 h-2 rounded-full ${liveStreamConnected ? 'bg-red-500 animate-ping' : (audioActive || cameraActive) ? 'bg-emerald-500 animate-pulse' : 'bg-slate-400'}`} />
            <span className="font-medium">{status}</span>
          </div>
          <div className="flex items-center gap-3 text-[11px] text-slate-500 font-mono">
            {hasRemoteAudio && <span className="text-emerald-600 font-bold">🔊 Audio In Browser</span>}
            {hasRemoteVideo && <span className="text-emerald-600 font-bold">📹 Video Synced</span>}
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-6">

          {/* Last Saved Stream Banner */}
          {lastSavedStream && (
            <div className="flex items-center justify-between p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-800 shadow-sm animate-fadeIn">
              <div className="flex items-center gap-2.5">
                <span className="text-lg">✅</span>
                <div>
                  <div className="font-bold">Stream Recording Stored!</div>
                  <div className="text-[11px] text-emerald-700 font-medium">
                    {lastSavedStream.type.toUpperCase()} stream &bull; {lastSavedStream.size} &bull; {lastSavedStream.duration}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <a
                  href={lastSavedStream.url}
                  download={lastSavedStream.filename}
                  className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold rounded-lg shadow-sm transition text-xs flex items-center gap-1.5"
                >
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                  </svg>
                  Download Stream
                </a>
                <a
                  href="/data/streaming"
                  className="px-3 py-1.5 bg-white hover:bg-slate-100 text-emerald-800 border border-emerald-300 font-semibold rounded-lg transition text-xs"
                >
                  Open Streaming Data &rarr;
                </a>
              </div>
            </div>
          )}

          {/* Camera Video Section */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-xs text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                📷 Live Camera Viewport
              </h3>
              <div className="flex items-center gap-2">
                <button
                  onClick={handleSwitchCamera}
                  disabled={isSwitchingCamera}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-amber-50 hover:bg-amber-100 border border-amber-300 text-amber-800 text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
                  title="Switch between front and back camera (Front ↔ Back)"
                >
                  <span className={isSwitchingCamera ? 'animate-spin inline-block' : ''}>🔄</span>
                  <span>{isSwitchingCamera ? 'Rotating...' : 'Rotate Camera'}</span>
                </button>

                {!cameraActive ? (
                  <button
                    onClick={handleStartCamera}
                    disabled={actionLoading}
                    className="px-4 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
                  >
                    {actionLoading ? 'Connecting...' : '▶ Start Camera Stream'}
                  </button>
                ) : (
                  <button
                    onClick={handleStopCamera}
                    disabled={actionLoading}
                    className="px-4 py-1.5 rounded-lg bg-red-600 hover:bg-red-700 text-white text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
                  >
                    {actionLoading ? 'Stopping...' : '■ Stop Camera'}
                  </button>
                )}
              </div>
            </div>

            {/* Video Viewport Container */}
            <div className="relative w-full h-72 bg-black rounded-xl border border-slate-200 overflow-hidden shadow-inner flex items-center justify-center">
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
                  <div className="text-4xl text-slate-500">
                    {cameraActive ? '📡' : '📹'}
                  </div>
                  <p className="text-sm font-semibold text-slate-300">
                    {cameraActive ? 'Connecting Live Video Stream...' : 'Camera Stream Inactive'}
                  </p>
                  <p className="text-xs text-slate-400 max-w-sm">
                    {cameraActive
                      ? 'Establishing real-time WebRTC channel with device. Live frame will appear immediately when received.'
                      : 'Click "Start Camera Stream" to wake the device camera and watch live video feed here.'}
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Audio Surveillance Section */}
          <div className="space-y-3 pt-2 border-t border-slate-200">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-xs text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                🎙️ Live Audio Listening & Sound Level
              </h3>
              <div className="flex gap-2">
                {!audioActive ? (
                  <button
                    onClick={handleStartAudio}
                    disabled={actionLoading}
                    className="px-4 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
                  >
                    {actionLoading ? 'Connecting...' : '▶ Listen Live (Browser Audio)'}
                  </button>
                ) : (
                  <button
                    onClick={handleStopAudio}
                    disabled={actionLoading}
                    className="px-4 py-1.5 rounded-lg bg-red-600 hover:bg-red-700 text-white text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
                  >
                    {actionLoading ? 'Stopping...' : '■ Mute & Stop Audio'}
                  </button>
                )}
              </div>
            </div>

            {/* dB level bar */}
            <div className="space-y-1 bg-slate-50 p-3 rounded-xl border border-slate-200">
              <div className="flex justify-between text-xs text-slate-500">
                <span className="font-medium">Ambient Noise Intensity</span>
                <span className="font-mono text-slate-900 font-bold">{dbDisplay} dB</span>
              </div>
              <DbBar db={currentDb} />
            </div>

            {/* Scrolling Waveform */}
            <WaveformCanvas history={history} />

            {/* Stats row */}
            <div className="grid grid-cols-3 gap-3 text-center">
              <div className="bg-slate-50 border border-slate-200 rounded-xl py-2.5">
                <p className="text-[11px] text-slate-500 uppercase font-semibold">Sound Level</p>
                <p className="text-base font-mono font-bold text-emerald-600 mt-0.5">{dbDisplay} dB</p>
              </div>
              <div className="bg-slate-50 border border-slate-200 rounded-xl py-2.5">
                <p className="text-[11px] text-slate-500 uppercase font-semibold">RMS Amplitude</p>
                <p className="text-base font-mono font-bold text-slate-800 mt-0.5">{currentRms.toFixed(0)}</p>
              </div>
              <div className="bg-slate-50 border border-slate-200 rounded-xl py-2.5">
                <p className="text-[11px] text-slate-500 uppercase font-semibold">Live Speaker</p>
                <p className={`text-base font-bold mt-0.5 ${hasRemoteAudio ? 'text-emerald-600 animate-pulse' : 'text-slate-400'}`}>
                  {hasRemoteAudio ? '🔊 Active' : 'Off'}
                </p>
              </div>
            </div>
          </div>

        </div>

        {/* Footer */}
        <div className="px-6 py-3 border-t border-slate-200 bg-slate-50 flex justify-between items-center">
          <span className="text-xs text-slate-400 font-mono">
            Device ID: {device.device_id.slice(0, 16)}…
          </span>
          <button
            onClick={() => {
              cleanupAgora();
              onClose();
            }}
            className="px-5 py-2 rounded-xl bg-slate-200 hover:bg-slate-300 text-xs font-semibold text-slate-700 transition cursor-pointer"
          >
            Close Surveillance
          </button>
        </div>
      </div>
    </div>
  );
}
