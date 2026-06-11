"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { fetchIceServers } from "@/lib/webrtc/ice-servers";
import { resolveTelemedicinePatientId } from "@/lib/webrtc/dev-test-facility";
import { RtcSignalingClient } from "@/lib/webrtc/signaling-client";
import { sendCallInvite } from "@/lib/webrtc/send-call-invite";
import type { RtcSignalMessage } from "@/lib/webrtc/types";

export type CallPhase = "idle" | "ringing" | "connecting" | "connected";

export interface UseWebRtcCallOptions {
  sessionId: string;
  peerId: string;
  /** Patient CPID anchor — used when provider rings patient. */
  patientId?: string;
  /** Provider/care-team user id — used when patient rings provider. */
  targetUserId?: string;
  callerName?: string;
  enabled?: boolean;
  /** User accepted an incoming invite (navigated with ?autoAnswer=1). */
  autoAnswer?: boolean;
  autoAnswerVideo?: boolean;
}

export interface UseWebRtcCallResult {
  callActive: boolean;
  callPhase: CallPhase;
  /** Call was started as a video consult (vs audio-only). */
  isVideoCall: boolean;
  videoEnabled: boolean;
  audioEnabled: boolean;
  connectionState: RTCPeerConnectionState | "idle";
  signalingStatus: string;
  remotePeerId: string | null;
  error: string | null;
  localVideoRef: React.RefObject<HTMLVideoElement>;
  remoteVideoRef: React.RefObject<HTMLVideoElement>;
  startCall: (options?: { video?: boolean }) => Promise<void>;
  endCall: () => void;
  toggleVideo: () => void;
  toggleAudio: () => void;
}

export function useWebRtcCall({
  sessionId,
  peerId,
  patientId,
  targetUserId,
  callerName,
  enabled = true,
  autoAnswer = false,
  autoAnswerVideo = false,
}: UseWebRtcCallOptions): UseWebRtcCallResult {
  const localVideoRef = useRef<HTMLVideoElement>(null);
  const remoteVideoRef = useRef<HTMLVideoElement>(null);
  const pcRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const remoteStreamRef = useRef<MediaStream | null>(null);
  const signalingRef = useRef<RtcSignalingClient | null>(null);
  const iceServersRef = useRef<RTCIceServer[] | null>(null);
  const pendingCandidatesRef = useRef<RTCIceCandidateInit[]>([]);
  const pendingOfferRef = useRef<RTCSessionDescriptionInit | null>(null);
  const makingOfferRef = useRef(false);
  const ignoreOfferRef = useRef(false);
  const remotePeerIdRef = useRef<string | null>(null);
  const callPhaseRef = useRef<CallPhase>("idle");
  const mediaReadyRef = useRef(false);
  const isCallerRef = useRef(false);
  const wantsVideoRef = useRef(false);
  const hasAutoAnsweredRef = useRef(false);
  const endedRef = useRef(false);
  const acceptRetryTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const endCallInternalRef = useRef<(fromRemote?: boolean) => void>(() => {});

  const [callActive, setCallActive] = useState(false);
  const [callPhase, setCallPhase] = useState<CallPhase>("idle");
  const [isVideoCall, setIsVideoCall] = useState(false);
  const [videoEnabled, setVideoEnabled] = useState(false);
  const [audioEnabled, setAudioEnabled] = useState(true);
  const [connectionState, setConnectionState] = useState<RTCPeerConnectionState | "idle">("idle");
  const [signalingStatus, setSignalingStatus] = useState("disconnected");
  const [remotePeerId, setRemotePeerId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const setPhase = useCallback((phase: CallPhase) => {
    callPhaseRef.current = phase;
    setCallPhase(phase);
    setCallActive(phase !== "idle");
  }, []);

  const isPolite = useCallback(() => {
    const remote = remotePeerIdRef.current;
    if (!remote) return true;
    return peerId > remote;
  }, [peerId]);

  const flushPendingCandidates = useCallback(async (pc: RTCPeerConnection) => {
    const pending = [...pendingCandidatesRef.current];
    pendingCandidatesRef.current = [];
    for (const candidate of pending) {
      try {
        await pc.addIceCandidate(new RTCIceCandidate(candidate));
      } catch {
        // stale candidate after reconnect
      }
    }
  }, []);

  const attachLocalPreview = useCallback((stream: MediaStream) => {
    if (localVideoRef.current) {
      localVideoRef.current.srcObject = stream;
    }
  }, []);

  const attachRemotePreview = useCallback((stream: MediaStream) => {
    if (remoteVideoRef.current) {
      remoteVideoRef.current.srcObject = stream;
    }
  }, []);

  const addRemoteTrack = useCallback(
    (track: MediaStreamTrack) => {
      if (!remoteStreamRef.current) {
        remoteStreamRef.current = new MediaStream();
        attachRemotePreview(remoteStreamRef.current);
      }
      if (!remoteStreamRef.current.getTracks().some((t) => t.id === track.id)) {
        remoteStreamRef.current.addTrack(track);
      }
    },
    [attachRemotePreview],
  );

  const ensurePeerConnection = useCallback(async () => {
    if (pcRef.current) return pcRef.current;

    if (!iceServersRef.current) {
      iceServersRef.current = await fetchIceServers();
    }

    const pc = new RTCPeerConnection({ iceServers: iceServersRef.current });

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        signalingRef.current?.send("ice-candidate", event.candidate.toJSON());
      }
    };

    pc.ontrack = (event) => {
      if (event.track) {
        addRemoteTrack(event.track);
      }
      event.streams[0]?.getTracks().forEach((track) => addRemoteTrack(track));
    };

    pc.onconnectionstatechange = () => {
      setConnectionState(pc.connectionState);
      if (pc.connectionState === "connected") {
        setPhase("connected");
        setError(null);
      }
      if (pc.connectionState === "failed") {
        setError("Connection failed — check camera/mic permissions and network.");
      }
      if (pc.connectionState === "disconnected" || pc.connectionState === "closed") {
        if (!endedRef.current) {
          setPhase("idle");
        }
      }
    };

    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((track) => {
        pc.addTrack(track, localStreamRef.current as MediaStream);
      });
    }

    pcRef.current = pc;
    return pc;
  }, [addRemoteTrack, setPhase]);

  const acquireMedia = useCallback(
    async (withVideo: boolean) => {
      if (localStreamRef.current) {
        localStreamRef.current.getTracks().forEach((t) => t.stop());
        localStreamRef.current = null;
      }
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: withVideo
          ? { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } }
          : false,
      });
      localStreamRef.current = stream;
      attachLocalPreview(stream);
      setAudioEnabled(stream.getAudioTracks().some((t) => t.enabled));
      setVideoEnabled(withVideo && stream.getVideoTracks().some((t) => t.enabled));
      wantsVideoRef.current = withVideo;
      mediaReadyRef.current = true;
      return stream;
    },
    [attachLocalPreview],
  );

  const stopAcceptRetry = useCallback(() => {
    if (acceptRetryTimerRef.current) {
      clearInterval(acceptRetryTimerRef.current);
      acceptRetryTimerRef.current = null;
    }
  }, []);

  const sendCallAccepted = useCallback(() => {
    signalingRef.current?.send("call-accepted");
  }, []);

  const startAcceptRetry = useCallback(() => {
    stopAcceptRetry();
    let attempts = 0;
    acceptRetryTimerRef.current = setInterval(() => {
      if (endedRef.current || isCallerRef.current || callPhaseRef.current === "idle") {
        stopAcceptRetry();
        return;
      }
      if (pcRef.current?.remoteDescription) {
        stopAcceptRetry();
        return;
      }
      attempts += 1;
      sendCallAccepted();
      if (attempts >= 12) {
        stopAcceptRetry();
      }
    }, 700);
  }, [sendCallAccepted, stopAcceptRetry]);

  const createAndSendOffer = useCallback(async () => {
    const pc = await ensurePeerConnection();
    if (makingOfferRef.current || pc.signalingState !== "stable") return;
    try {
      makingOfferRef.current = true;
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      signalingRef.current?.send("offer", offer);
      setPhase("connecting");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not start call negotiation");
    } finally {
      makingOfferRef.current = false;
    }
  }, [ensurePeerConnection, setPhase]);

  const beginMediaSession = useCallback(
    async (withVideo: boolean) => {
      if (mediaReadyRef.current && callPhaseRef.current === "connecting") return;
      setError(null);
      setIsVideoCall(withVideo);
      wantsVideoRef.current = withVideo;
      setPhase("connecting");

      if (!isCallerRef.current) {
        sendCallAccepted();
        startAcceptRetry();
      }

      try {
        await acquireMedia(withVideo);
        const pc = await ensurePeerConnection();

        if (pendingOfferRef.current) {
          const offer = pendingOfferRef.current;
          pendingOfferRef.current = null;
          await pc.setRemoteDescription(new RTCSessionDescription(offer));
          await flushPendingCandidates(pc);
          const answer = await pc.createAnswer();
          await pc.setLocalDescription(answer);
          signalingRef.current?.send("answer", answer);
          stopAcceptRetry();
        } else if (isCallerRef.current) {
          await createAndSendOffer();
        }
      } catch (e) {
        stopAcceptRetry();
        setError(e instanceof Error ? e.message : "Could not access camera/microphone");
        mediaReadyRef.current = false;
        setIsVideoCall(false);
        setPhase("idle");
      }
    },
    [
      acquireMedia,
      createAndSendOffer,
      ensurePeerConnection,
      flushPendingCandidates,
      sendCallAccepted,
      setPhase,
      startAcceptRetry,
      stopAcceptRetry,
    ],
  );

  const maybeStartCallerMedia = useCallback(async () => {
    if (!isCallerRef.current || endedRef.current) return;
    if (callPhaseRef.current !== "ringing" && callPhaseRef.current !== "connecting") return;

    if (mediaReadyRef.current) {
      const pc = pcRef.current;
      if (pc && pc.signalingState === "stable" && !makingOfferRef.current) {
        await createAndSendOffer();
      }
      return;
    }

    await beginMediaSession(wantsVideoRef.current);
  }, [beginMediaSession, createAndSendOffer]);

  const processOffer = useCallback(
    async (offer: RTCSessionDescriptionInit) => {
      if (!mediaReadyRef.current) {
        pendingOfferRef.current = offer;
        return;
      }

      const pc = await ensurePeerConnection();
      try {
        const offerCollision = makingOfferRef.current || pc.signalingState !== "stable";
        ignoreOfferRef.current = !isPolite() && offerCollision;
        if (ignoreOfferRef.current) return;

        await pc.setRemoteDescription(new RTCSessionDescription(offer));
        await flushPendingCandidates(pc);
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        signalingRef.current?.send("answer", answer);
        stopAcceptRetry();
        setPhase("connecting");
      } catch (e) {
        setError(e instanceof Error ? e.message : "Signaling error");
      }
    },
    [ensurePeerConnection, flushPendingCandidates, isPolite, setPhase, stopAcceptRetry],
  );

  const endCallInternal = useCallback(
    (fromRemote = false) => {
      if (!fromRemote && signalingRef.current && callPhaseRef.current !== "idle") {
        signalingRef.current.send("hangup");
      }

      endedRef.current = true;
      stopAcceptRetry();
      mediaReadyRef.current = false;
      isCallerRef.current = false;
      hasAutoAnsweredRef.current = false;
      wantsVideoRef.current = false;
      pendingOfferRef.current = null;
      pendingCandidatesRef.current = [];
      makingOfferRef.current = false;
      ignoreOfferRef.current = false;
      remotePeerIdRef.current = null;
      setRemotePeerId(null);

      localStreamRef.current?.getTracks().forEach((t) => t.stop());
      localStreamRef.current = null;
      remoteStreamRef.current?.getTracks().forEach((t) => t.stop());
      remoteStreamRef.current = null;

      pcRef.current?.close();
      pcRef.current = null;

      if (localVideoRef.current) localVideoRef.current.srcObject = null;
      if (remoteVideoRef.current) remoteVideoRef.current.srcObject = null;

      setVideoEnabled(false);
      setIsVideoCall(false);
      setAudioEnabled(true);
      setConnectionState("idle");
      setError(null);
      setPhase("idle");

      window.setTimeout(() => {
        endedRef.current = false;
      }, 300);
    },
    [setPhase, stopAcceptRetry],
  );

  endCallInternalRef.current = endCallInternal;

  const handleSignal = useCallback(
    async (msg: RtcSignalMessage) => {
      if (endedRef.current) return;

      if (msg.from && msg.from !== peerId) {
        remotePeerIdRef.current = msg.from;
        setRemotePeerId(msg.from);
      }

      if (msg.type === "hangup") {
        endCallInternalRef.current(true);
        return;
      }

      if (msg.type === "call-accepted") {
        await maybeStartCallerMedia();
        return;
      }

      if (
        (msg.type === "peer-joined" || msg.type === "joined") &&
        isCallerRef.current &&
        (msg.peerCount ?? 0) >= 2
      ) {
        await maybeStartCallerMedia();
        return;
      }

      if (msg.type === "peer-left") {
        remotePeerIdRef.current = null;
        setRemotePeerId(null);
        if (remoteStreamRef.current) {
          remoteStreamRef.current.getTracks().forEach((t) => t.stop());
          remoteStreamRef.current = null;
        }
        if (remoteVideoRef.current) remoteVideoRef.current.srcObject = null;
        return;
      }

      if (msg.type === "offer" && msg.payload) {
        await processOffer(msg.payload as RTCSessionDescriptionInit);
        return;
      }

      const pc = pcRef.current;
      if (!pc || !msg.payload) return;

      try {
        if (msg.type === "answer") {
          if (pc.signalingState === "have-local-offer") {
            await pc.setRemoteDescription(
              new RTCSessionDescription(msg.payload as RTCSessionDescriptionInit),
            );
            await flushPendingCandidates(pc);
          }
        } else if (msg.type === "ice-candidate") {
          const candidate = msg.payload as RTCIceCandidateInit;
          if (pc.remoteDescription) {
            await pc.addIceCandidate(new RTCIceCandidate(candidate));
          } else {
            pendingCandidatesRef.current.push(candidate);
          }
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Signaling error");
      }
    },
    [maybeStartCallerMedia, peerId, processOffer, flushPendingCandidates],
  );

  useEffect(() => {
    if (!enabled || !sessionId || !peerId) return;

    const client = new RtcSignalingClient(sessionId, peerId, handleSignal, setSignalingStatus);
    signalingRef.current = client;
    client.connect();

    return () => {
      client.close();
      signalingRef.current = null;
    };
  }, [enabled, sessionId, peerId, handleSignal]);

  const startCall = useCallback(
    async (options?: { video?: boolean }) => {
      if (callPhaseRef.current !== "idle") return;
      const resolvedPatientId = patientId ? resolveTelemedicinePatientId(patientId) : "";
      if (!targetUserId && !resolvedPatientId) {
        setError("Cannot reach the other party — reopen the session or create a new one.");
        return;
      }
      endedRef.current = false;
      setError(null);
      const withVideo = options?.video ?? false;
      wantsVideoRef.current = withVideo;
      setIsVideoCall(withVideo);
      isCallerRef.current = true;

      setPhase("ringing");

      void (async () => {
        try {
          if (targetUserId) {
            await sendCallInvite({
              sessionId,
              targetUserId,
              callerName,
              callType: withVideo ? "video" : "audio",
            });
          } else if (resolvedPatientId) {
            await sendCallInvite({
              sessionId,
              patientId: resolvedPatientId,
              callerName,
              callType: withVideo ? "video" : "audio",
            });
          }
        } catch {
          setError("Could not notify the other party — they may already be in the session.");
        }
      })();
    },
    [callerName, patientId, sessionId, setPhase, targetUserId],
  );

  useEffect(() => {
    if (!autoAnswer) return;
    if (hasAutoAnsweredRef.current || callPhaseRef.current !== "idle") return;
    hasAutoAnsweredRef.current = true;
    isCallerRef.current = false;
    endedRef.current = false;
    void beginMediaSession(autoAnswerVideo);
  }, [autoAnswer, autoAnswerVideo, beginMediaSession]);

  // Allow accepting another invite on the same page after URL params are cleared.
  useEffect(() => {
    if (!autoAnswer && callPhaseRef.current === "idle") {
      hasAutoAnsweredRef.current = false;
    }
  }, [autoAnswer]);

  const endCall = useCallback(() => {
    endCallInternal(false);
  }, [endCallInternal]);

  const toggleVideo = useCallback(() => {
    const track = localStreamRef.current?.getVideoTracks()[0];
    if (!track) return;
    track.enabled = !track.enabled;
    setVideoEnabled(track.enabled);
  }, []);

  const toggleAudio = useCallback(() => {
    const track = localStreamRef.current?.getAudioTracks()[0];
    if (!track) return;
    track.enabled = !track.enabled;
    setAudioEnabled(track.enabled);
  }, []);

  useEffect(() => {
    return () => {
      endCallInternalRef.current(false);
    };
  }, []);

  return {
    callActive,
    callPhase,
    isVideoCall,
    videoEnabled,
    audioEnabled,
    connectionState,
    signalingStatus,
    remotePeerId,
    error,
    localVideoRef,
    remoteVideoRef,
    startCall,
    endCall,
    toggleVideo,
    toggleAudio,
  };
}
