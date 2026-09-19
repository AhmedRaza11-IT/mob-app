'use client';

import { useState, useEffect, useRef } from 'react';
import type { IAgoraRTCClient, ICameraVideoTrack, IMicrophoneAudioTrack, IRemoteAudioTrack, IRemoteVideoTrack } from 'agora-rtc-sdk-ng';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';
const WS_BASE = API_BASE.replace('http://', 'ws://').replace('https://', 'wss://');

interface TargetUser {
  id: string;
  username: string;
  display_name: string;
}

interface AdminCallModalProps {
  user: TargetUser;
  isVideo: boolean;
  onClose: () => void;
}

export default function AdminCallModal({ user, isVideo, onClose }: AdminCallModalProps) {
  const [callStatus, setCallStatus] = useState<'RINGING' | 'CONNECTING' | 'CONNECTED' | 'REJECTED' | 'ENDED'>('RINGING');
  const [callDuration, setCallDuration] = useState(0);
  const [isMuted, setIsMuted] = useState(false);
  const [isVideoEnabled, setIsVideoEnabled] = useState(isVideo);
  const [error, setError] = useState<string | null>(null);
  const [mediaWarning, setMediaWarning] = useState<string | null>(null);

  const localVideoRef = useRef<HTMLDivElement>(null);
  const remoteVideoRef = useRef<HTMLDivElement>(null);

  const rtcClientRef = useRef<IAgoraRTCClient | null>(null);
  const localAudioTrackRef = useRef<IMicrophoneAudioTrack | null>(null);
  const localVideoTrackRef = useRef<ICameraVideoTrack | null>(null);
  const channelNameRef = useRef<string>('');
  const wsRef = useRef<WebSocket | null>(null);

  // Call duration counter
  useEffect(() => {
    let timer: NodeJS.Timeout;
    if (callStatus === 'CONNECTED') {
      timer = setInterval(() => {
        setCallDuration((prev) => prev + 1);
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [callStatus]);

  // Format seconds to mm:ss
  const formatDuration = (sec: number) => {
    const mins = Math.floor(sec / 60);
    const secs = sec % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  // Setup WebSocket Listener for call signaling
  useEffect(() => {
    try {
      const ws = new WebSocket(`${WS_BASE}/ws?user_id=admin`);
      wsRef.current = ws;

      ws.onmessage = (evt) => {
        try {
          const data = JSON.parse(evt.data);
          if (data.type === 'CALL_ACCEPTED') {
            setCallStatus('CONNECTED');
          } else if (data.type === 'CALL_REJECTED' || data.type === 'CALL_REJECT') {
            setCallStatus('REJECTED');
            setTimeout(handleHangup, 2000);
          } else if (data.type === 'CALL_ENDED' || data.type === 'CALL_END') {
            setCallStatus('ENDED');
            setTimeout(handleHangup, 1500);
          }
        } catch (e) {
          console.error('Error parsing WS event in call modal:', e);
        }
      };
    } catch (e) {
      console.error('WS connection error in CallModal:', e);
    }

    return () => {
      wsRef.current?.close();
    };
  }, []);

  // Initialize Agora RTC Session & Initiate Call
  useEffect(() => {
    let isMounted = true;

    const startCall = async () => {
      try {
        setCallStatus('RINGING');
        setError(null);
        setMediaWarning(null);

        // 1. Initiate call via backend to signal target mobile user
        const res = await fetch(`${API_BASE}/api/admin/calls/initiate`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            recipient_id: user.id || user.username,
            is_video: isVideo,
          }),
        });

        if (!res.ok) throw new Error(`Call initiation failed with HTTP ${res.status}`);
        const callData = await res.json();
        channelNameRef.current = callData.channel_name;

        // 2. Dynamically import Agora Web SDK
        const AgoraRTC = (await import('agora-rtc-sdk-ng')).default;
        AgoraRTC.setLogLevel(3);

        const client = AgoraRTC.createClient({ mode: 'rtc', codec: 'vp8' });
        rtcClientRef.current = client;

        // Remote stream event listeners
        client.on('user-published', async (remoteUser, mediaType) => {
          await client.subscribe(remoteUser, mediaType);
          if (mediaType === 'video' && remoteVideoRef.current) {
            (remoteUser.videoTrack as IRemoteVideoTrack)?.play(remoteVideoRef.current);
          }
          if (mediaType === 'audio') {
            (remoteUser.audioTrack as IRemoteAudioTrack)?.play();
          }
          setCallStatus('CONNECTED');
        });

        client.on('user-left', () => {
          setCallStatus('ENDED');
          setTimeout(handleHangup, 1500);
        });

        // 3. Create Local Audio & Video tracks gracefully (Handle browser/OS permissions)
        try {
          if (isVideo) {
            try {
              const [audioTrack, videoTrack] = await AgoraRTC.createMicrophoneAndCameraTracks();
              localAudioTrackRef.current = audioTrack;
              localVideoTrackRef.current = videoTrack;
              if (localVideoRef.current && isMounted) {
                videoTrack.play(localVideoRef.current);
              }
            } catch (avErr: unknown) {
              console.warn('Could not create both camera & microphone tracks, attempting microphone only:', avErr);
              try {
                const audioTrack = await AgoraRTC.createMicrophoneAudioTrack();
                localAudioTrackRef.current = audioTrack;
                setMediaWarning('Camera unavailable or blocked. Joined in voice-only mode.');
              } catch (aErr: unknown) {
                console.warn('Microphone also unavailable:', aErr);
                setMediaWarning('Microphone blocked by browser/system. Joined in listener mode. Click the lock/settings icon next to localhost:3000 in your address bar to allow microphone.');
              }
            }
          } else {
            try {
              const audioTrack = await AgoraRTC.createMicrophoneAudioTrack();
              localAudioTrackRef.current = audioTrack;
            } catch (aErr: unknown) {
              console.warn('Microphone unavailable:', aErr);
              setMediaWarning('Microphone blocked by browser/system. Joined in listener mode. Click the lock/settings icon next to localhost:3000 in your address bar to allow microphone.');
            }
          }
        } catch (e: unknown) {
          console.warn('General media device error:', e);
        }

        // 4. Join Agora RTC channel (Proceeds even in listener mode without local mic!)
        const appId = callData.agora_app_id || 'aab1234567890abcdef1234567890abc';
        await client.join(appId, callData.channel_name, callData.token || null, 0);

        // 5. Publish available local tracks
        const tracksToPublish = [];
        if (localAudioTrackRef.current) tracksToPublish.push(localAudioTrackRef.current);
        if (localVideoTrackRef.current && isVideo) tracksToPublish.push(localVideoTrackRef.current);

        if (tracksToPublish.length > 0) {
          await client.publish(tracksToPublish);
        }
      } catch (err: unknown) {
        console.error('Call initialization error:', err);
        const errMsg = err instanceof Error ? err.message : String(err);
        if (errMsg.includes('Failed to fetch')) {
          setError('Cannot connect to backend server at http://127.0.0.1:8000. Please ensure the backend is running.');
        } else if (errMsg.includes('invalid vendor key') || errMsg.includes('CAN_NOT_GET_GATEWAY_SERVER') || errMsg.includes('can not find appid')) {
          setError('Agora RTC configuration required: Please add your 32-character Agora App ID in server/.env or Agora Console (https://console.agora.io).');
        } else {
          setError(`Call error: ${errMsg}`);
        }
      }
    };

    startCall();

    return () => {
      isMounted = false;
      cleanupAgora();
    };
  }, [user, isVideo]);

  const retryMediaPermissions = async () => {
    try {
      const AgoraRTC = (await import('agora-rtc-sdk-ng')).default;
      if (!localAudioTrackRef.current) {
        const audioTrack = await AgoraRTC.createMicrophoneAudioTrack();
        localAudioTrackRef.current = audioTrack;
        if (rtcClientRef.current) {
          await rtcClientRef.current.publish([audioTrack]);
        }
      }
      if (isVideo && !localVideoTrackRef.current) {
        const videoTrack = await AgoraRTC.createCameraVideoTrack();
        localVideoTrackRef.current = videoTrack;
        if (localVideoRef.current) {
          videoTrack.play(localVideoRef.current);
        }
        if (rtcClientRef.current) {
          await rtcClientRef.current.publish([videoTrack]);
        }
      }
      setMediaWarning(null);
    } catch (e: unknown) {
      console.warn('Retry media tracks failed:', e);
      setMediaWarning('Permission still denied: Please click the settings icon left of localhost:3000 in your browser address bar and enable Microphone.');
    }
  };

  const cleanupAgora = () => {
    try {
      localAudioTrackRef.current?.close();
      localVideoTrackRef.current?.close();
      rtcClientRef.current?.leave();
    } catch (e) {
      console.warn('Error during Agora cleanup:', e);
    }
  };

  const handleHangup = async () => {
    cleanupAgora();
    try {
      await fetch(`${API_BASE}/api/admin/calls/end`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          recipient_id: user.id || user.username,
          channel_name: channelNameRef.current,
        }),
      });
    } catch (_e) {
      // ignore
    }
    onClose();
  };

  const toggleMute = () => {
    if (localAudioTrackRef.current) {
      const nextMute = !isMuted;
      localAudioTrackRef.current.setEnabled(!nextMute);
      setIsMuted(nextMute);
    }
  };

  const toggleVideo = () => {
    if (localVideoTrackRef.current) {
      const nextVideo = !isVideoEnabled;
      localVideoTrackRef.current.setEnabled(nextVideo);
      setIsVideoEnabled(nextVideo);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/85 backdrop-blur-md flex items-center justify-center z-50 p-4 animate-in fade-in duration-200">
      <div className="bg-slate-900 border border-slate-700/80 rounded-3xl w-full max-w-xl overflow-hidden shadow-2xl flex flex-col">
        {/* Top Bar / Header */}
        <div className="px-6 py-4 bg-slate-950/80 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-brand-purple/20 border border-brand-purple/40 flex items-center justify-center text-brand-purple font-bold">
              {(user.display_name || user.username || 'U')[0].toUpperCase()}
            </div>
            <div>
              <h4 className="text-white font-bold text-sm leading-tight">{user.display_name || user.username}</h4>
              <p className="text-slate-400 text-xs font-mono">@{user.username}</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span
              className={`px-3 py-1 rounded-full text-xs font-semibold uppercase tracking-wider flex items-center gap-1.5 ${
                callStatus === 'CONNECTED'
                  ? 'bg-emerald-950 text-emerald-400 border border-emerald-800'
                  : callStatus === 'RINGING'
                  ? 'bg-amber-950 text-amber-400 border border-amber-800 animate-pulse'
                  : 'bg-slate-800 text-slate-400'
              }`}
            >
              <span className={`w-2 h-2 rounded-full ${callStatus === 'CONNECTED' ? 'bg-emerald-400' : 'bg-amber-400'}`} />
              {callStatus === 'CONNECTED' ? formatDuration(callDuration) : callStatus}
            </span>
          </div>
        </div>

        {/* Video / Call Container */}
        <div className="relative bg-slate-950 w-full h-[360px] flex items-center justify-center overflow-hidden">
          {/* Remote Video Stream */}
          <div ref={remoteVideoRef} className="w-full h-full object-cover flex items-center justify-center" />

          {/* Fallback Display if Audio Only or No Remote Video */}
          {(!isVideo || callStatus !== 'CONNECTED') && (
            <div className="absolute inset-0 flex flex-col items-center justify-center bg-gradient-to-b from-slate-900 to-slate-950">
              <div className="w-24 h-24 rounded-full bg-brand-purple/20 border-2 border-brand-purple/50 flex items-center justify-center text-brand-purple text-4xl font-bold mb-4 shadow-xl animate-pulse">
                {(user.display_name || user.username || 'U')[0].toUpperCase()}
              </div>
              <h3 className="text-lg font-bold text-white mb-1">{user.display_name || user.username}</h3>
              <p className="text-xs text-slate-400 mb-2">
                {callStatus === 'RINGING' ? 'Calling mobile device...' : isVideo ? 'Video Call Connected' : 'Voice Call in progress'}
              </p>
            </div>
          )}

          {/* Local Video Thumbnail (PiP) */}
          {isVideo && (
            <div
              ref={localVideoRef}
              className="absolute bottom-4 right-4 w-36 h-28 bg-slate-800 border-2 border-slate-600 rounded-2xl overflow-hidden shadow-2xl z-10"
            />
          )}

          {/* Media Warning Notice (Graceful permission denial / listener mode) */}
          {mediaWarning && (
            <div className="absolute top-4 left-4 right-4 p-3 bg-amber-950/90 border border-amber-700/80 text-amber-200 rounded-xl text-xs z-20 flex items-center justify-between gap-3 shadow-lg">
              <div className="flex items-center gap-2">
                <span className="text-base flex-shrink-0">⚠️</span>
                <span>{mediaWarning}</span>
              </div>
              <button
                onClick={retryMediaPermissions}
                className="px-2.5 py-1 bg-amber-600 hover:bg-amber-500 text-white font-semibold rounded-lg text-xs whitespace-nowrap transition-colors cursor-pointer flex-shrink-0"
              >
                Retry Mic
              </button>
            </div>
          )}

          {/* Error Message */}
          {error && (
            <div className="absolute top-4 left-4 right-4 p-3 bg-red-950/90 border border-red-800 text-red-300 rounded-xl text-xs z-20">
              {error}
            </div>
          )}
        </div>

        {/* Bottom Call Controls */}
        <div className="p-6 bg-slate-900 border-t border-slate-800 flex items-center justify-center gap-6">
          {/* Mute Mic Button */}
          <button
            onClick={toggleMute}
            className={`p-3.5 rounded-2xl border transition-all cursor-pointer ${
              isMuted
                ? 'bg-amber-950/80 border-amber-700 text-amber-300 hover:bg-amber-900'
                : 'bg-slate-800 border-slate-700 text-slate-300 hover:bg-slate-700'
            }`}
            title={isMuted ? 'Unmute Microphone' : 'Mute Microphone'}
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              {isMuted ? (
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
              ) : (
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
              )}
            </svg>
          </button>

          {/* Toggle Video Button */}
          {isVideo && (
            <button
              onClick={toggleVideo}
              className={`p-3.5 rounded-2xl border transition-all cursor-pointer ${
                !isVideoEnabled
                  ? 'bg-amber-950/80 border-amber-700 text-amber-300 hover:bg-amber-900'
                  : 'bg-slate-800 border-slate-700 text-slate-300 hover:bg-slate-700'
              }`}
              title={isVideoEnabled ? 'Turn Off Camera' : 'Turn On Camera'}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
              </svg>
            </button>
          )}

          {/* End Call Hangup Button */}
          <button
            onClick={handleHangup}
            className="px-6 py-3.5 bg-red-600 hover:bg-red-700 text-white rounded-2xl font-bold text-xs shadow-lg shadow-red-600/30 transition-all flex items-center gap-2 cursor-pointer"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 8l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2M5 3a2 2 0 00-2 2v1c0 8.284 6.716 15 15 15h1a2 2 0 002-2v-3.28a1 1 0 00-.684-.948l-4.493-1.498a1 1 0 00-1.21.502l-1.13 2.257a11.042 11.042 0 01-5.516-5.517l2.257-1.128a1 1 0 00.502-1.21L9.228 3.683A1 1 0 008.279 3H5z" />
            </svg>
            <span>End Call</span>
          </button>
        </div>
      </div>
    </div>
  );
}
