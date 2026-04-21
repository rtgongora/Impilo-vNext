import { describe, it, expect } from "vitest";
import type {
  Notification,
  Conversation,
  Message,
  SendMessageRequest,
  PushRegistration,
  NotificationType,
  NotificationCategory,
  NotificationPriority,
  ConversationType,
  MessageContentType,
  MessageStatus,
} from "../src/types";

describe("Notification type", () => {
  it("accepts valid notification objects", () => {
    const n: Notification = {
      id: "n-1",
      type: "IN_APP",
      title: "Result Ready",
      body: "Your lab results are available.",
      category: "RESULT_READY",
      priority: "HIGH",
      read: false,
      createdAt: new Date().toISOString(),
    };
    expect(n.id).toBe("n-1");
    expect(n.read).toBe(false);
  });

  it("accepts the readAt back-compat field", () => {
    const n: Notification = {
      id: "n-2",
      type: "PUSH",
      title: "Appointment",
      body: "Reminder",
      category: "APPOINTMENT_REMINDER",
      priority: "NORMAL",
      read: true,
      readAt: new Date().toISOString(),
      createdAt: new Date().toISOString(),
    };
    expect(n.readAt).toBeDefined();
  });

  it("supports all notification types", () => {
    const types: NotificationType[] = ["PUSH", "IN_APP", "SILENT"];
    expect(types).toHaveLength(3);
  });

  it("supports all notification categories", () => {
    const cats: NotificationCategory[] = [
      "TASK_ASSIGNED",
      "RESULT_READY",
      "MESSAGE_RECEIVED",
      "APPOINTMENT_REMINDER",
      "RX_READY",
      "ESCALATION",
      "SYSTEM",
      "SYNC_COMPLETE",
    ];
    expect(cats).toHaveLength(8);
  });

  it("supports all priority levels", () => {
    const priorities: NotificationPriority[] = ["HIGH", "NORMAL", "LOW"];
    expect(priorities).toHaveLength(3);
  });
});

describe("Conversation type", () => {
  it("accepts valid conversation objects", () => {
    const c: Conversation = {
      id: "conv-1",
      type: "DIRECT",
      participants: [
        { actorId: "a-1", actorType: "PROVIDER", displayName: "Dr. Moyo", role: "OWNER" },
      ],
      unreadCount: 2,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    expect(c.unreadCount).toBe(2);
  });

  it("supports the title back-compat alias", () => {
    const c: Conversation = {
      id: "conv-2",
      type: "GROUP",
      participants: [],
      title: "Team Chat",
      unreadCount: 0,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    expect(c.title).toBe("Team Chat");
  });

  it("supports all conversation types", () => {
    const types: ConversationType[] = ["DIRECT", "GROUP", "SUPPORT", "SYSTEM"];
    expect(types).toHaveLength(4);
  });
});

describe("Message type", () => {
  it("accepts valid message objects", () => {
    const m: Message = {
      id: "msg-1",
      conversationId: "conv-1",
      senderId: "a-1",
      senderName: "Dr. Moyo",
      body: "Hello",
      contentType: "TEXT",
      sentAt: new Date().toISOString(),
      readBy: [],
      status: "SENT",
    };
    expect(m.body).toBe("Hello");
    expect(m.status).toBe("SENT");
  });

  it("supports the content back-compat alias", () => {
    const m: Message = {
      id: "msg-2",
      conversationId: "conv-1",
      senderId: "a-1",
      senderName: "Dr. Moyo",
      body: "Test",
      content: "Test",
      contentType: "TEXT",
      sentAt: new Date().toISOString(),
      readBy: [],
      status: "DELIVERED",
    };
    expect(m.content).toBe(m.body);
  });

  it("supports all message content types", () => {
    const types: MessageContentType[] = [
      "TEXT",
      "RICH_TEXT",
      "IMAGE",
      "DOCUMENT",
      "SYSTEM",
    ];
    expect(types).toHaveLength(5);
  });

  it("supports all message statuses", () => {
    const statuses: MessageStatus[] = [
      "SENDING",
      "SENT",
      "DELIVERED",
      "READ",
      "FAILED",
    ];
    expect(statuses).toHaveLength(5);
  });
});

describe("SendMessageRequest type", () => {
  it("accepts minimal send request", () => {
    const req: SendMessageRequest = {
      conversationId: "conv-1",
      body: "Hello",
    };
    expect(req.conversationId).toBe("conv-1");
    expect(req.contentType).toBeUndefined();
  });
});

describe("PushRegistration type", () => {
  it("accepts valid push registration", () => {
    const reg: PushRegistration = {
      deviceId: "device-abc",
      platform: "android",
      pushToken: "tok-xyz",
      appVersion: "0.1.0",
    };
    expect(reg.platform).toBe("android");
  });
});
