export type RtcSignalType =
  | "join"
  | "joined"
  | "peer-joined"
  | "peer-left"
  | "offer"
  | "answer"
  | "ice-candidate"
  | "hangup"
  | "call-accepted"
  | "call-invite"
  | "ping"
  | "pong";

export interface IncomingCallInvite {
  type: "call-invite";
  sessionId: string;
  patientId?: string;
  targetUserId?: string;
  from?: string;
  callerName?: string;
  callType?: "audio" | "video" | string;
  createdAt?: string;
}

export interface RtcSignalMessage {
  type: RtcSignalType;
  from?: string;
  roomId?: string;
  peerId?: string;
  peerCount?: number;
  peers?: string[];
  payload?: RTCSessionDescriptionInit | RTCIceCandidateInit;
}

export interface IceServerConfig {
  urls: string | string[];
  username?: string;
  credential?: string;
}
