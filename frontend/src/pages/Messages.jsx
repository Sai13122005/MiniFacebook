
import { useEffect, useRef, useState } from "react";
import { MessageCircle, Send, RefreshCw } from "lucide-react";
import { useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { sendRequest } from "../services/api";
import { connectMessageSocket } from "../services/messageSocket";

import "./Messages.css";

export default function Messages() {
    const { user, sessionToken } = useAuth();
    const messagesEndRef = useRef(null);
    const socketRef = useRef(null);
    const activeUserRef = useRef(null);
    const location = useLocation();
    const initialSelectedUser = location.state?.selectedUser;
    const [conversations, setConversations] = useState([]);
    const [selectedUser, setSelectedUser] = useState(null);
    const [messages, setMessages] = useState([]);
    const [content, setContent] = useState("");

    const [connected, setConnected] = useState(false);
    const [loading, setLoading] = useState(true);
    const [sending, setSending] = useState(false);
    const [error, setError] = useState("");

    async function loadConversations() {
        try {
            const result = await sendRequest(
                "GET_CONVERSATIONS",
                { sessionToken }
            );

            if (result.status !== "SUCCESS") {
                throw new Error(result.message || "Unable to load conversations");
            }

            setConversations(result.conversations || []);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }

    async function loadConversation(otherUser) {
        setSelectedUser(otherUser);
        activeUserRef.current = otherUser.userId;
        setMessages([]);
        setError("");

        try {
            const result = await sendRequest(
                "GET_CONVERSATION",
                {
                    sessionToken,
                    otherUserId: otherUser.userId
                }
            );

            if (result.status !== "SUCCESS") {
                throw new Error(result.message || "Unable to load messages");
            }

            // Avoid displaying an old conversation if the user switched chats.
            if (activeUserRef.current !== otherUser.userId) return;

            setMessages(
                (result.messages || []).map(message => ({
                    ...message,
                    read:
                        message.senderId !== otherUser.userId
                            ? message.read
                            : true
                }))
            );

            // Immediately clear the unread badge for this conversation.
            setConversations(previous =>
                previous.map(person =>
                    person.userId === otherUser.userId
                        ? { ...person, unreadCount: 0 }
                        : person
                )
            );
            window.dispatchEvent(
                new CustomEvent("messages-read", {
                    detail: { userId: otherUser.userId }
                })
            );
            // Tell the backend to persist the read status.
            if (socketRef.current?.isConnected) {
                socketRef.current.markMessagesRead(otherUser.userId);
            }
        } catch (err) {
            setError(err.message);
        }
    }

    useEffect(() => {
        loadConversations();
    }, [sessionToken]);

    useEffect(() => {
        if (!sessionToken) return;

        let socketConnection;

        try {
            socketConnection = connectMessageSocket({
                onConnected: async () => {
                    setConnected(true);

                    // Refresh the conversation list after reconnecting.
                    loadConversations();

                    // Restore the currently open conversation.
                    const otherUserId = activeUserRef.current;

                    if (otherUserId) {
                        try {
                            const result = await sendRequest(
                                "GET_CONVERSATION",
                                {
                                    sessionToken,
                                    otherUserId
                                }
                            );

                            if (
                                result.status === "SUCCESS" &&
                                activeUserRef.current === otherUserId
                            ) {
                                setMessages(result.messages || []);

                                socketRef.current?.markMessagesRead(otherUserId);
                            }
                        } catch (error) {
                            console.error("Unable to restore conversation:", error);
                        }
                    }
                },

                onDisconnected: () => {
                    setConnected(false);
                },

                onError: (message) => {
                    setError(message);
                },

                onMessage: (event) => {
                    const message = event.messageData;
                    if (event.event === "MESSAGE_READ") {

                        setMessages(previous =>
                            previous.map(message => {

                                if (
                                    message.senderId === user?.userId &&
                                    message.receiverId === event.readerId
                                ) {
                                    return {
                                        ...message,
                                        read: true
                                    };
                                }

                                return message;
                            })
                        );

                        return;
                    }
                    if (!message) return;

                    const currentChatUser = activeUserRef.current;

                    const belongsToCurrentChat =
                        currentChatUser &&
                        (
                            (message.senderId === user?.userId &&
                                message.receiverId === currentChatUser) ||
                            (message.receiverId === user?.userId &&
                                message.senderId === currentChatUser)
                        );

                    if (belongsToCurrentChat) {
                        setMessages(previous => {
                            if (previous.some(
                                item => item.messageId === message.messageId
                            )) {
                                return previous;
                            }

                            return [...previous, message];
                        });

                        if (message.receiverId === user?.userId) {
                            socketRef.current?.markMessagesRead(message.senderId);
                        }
                    }

                    if (
                        event.event === "NEW_MESSAGE" ||
                        event.event === "MESSAGE_SENT"
                    ) {
                        loadConversations();
                    }
                }
            });

            socketRef.current = socketConnection;
        } catch (err) {
            setError(err.message);
        }

        return () => {
            socketConnection?.disconnect();
            socketRef.current = null;
            setConnected(false);
        };
    }, [sessionToken, user?.userId]);
    useEffect(() => {
        if (!initialSelectedUser?.userId) return;

        // Add the selected user to the sidebar if not already present.
        setConversations(previous => {
            const exists = previous.some(
                person => person.userId === initialSelectedUser.userId
            );

            return exists
                ? previous
                : [...previous, initialSelectedUser];
        });

        // Open the selected user's conversation.
        loadConversation(initialSelectedUser);

        // Clear navigation state.
        window.history.replaceState(
            {},
            document.title,
            window.location.pathname
        );
    }, [initialSelectedUser?.userId]);
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({
            behavior: "smooth",
            block: "end",
        });
    }, [messages]);
    async function handleSendMessage(event) {
        event.preventDefault();

        if (!selectedUser || !content.trim()) return;

        if (!connected || !socketRef.current) {
            setError("Messaging connection is not available. Please reconnect.");
            return;
        }

        try {
            setSending(true);
            setError("");

            socketRef.current.sendMessage(
                selectedUser.userId,
                content.trim()
            );

            setContent("");
        } catch (err) {
            setError(err.message);
        } finally {
            setSending(false);
        }
    }

    function formatTime(timestamp) {
        if (!timestamp) return "";

        const date = new Date(timestamp);

        if (Number.isNaN(date.getTime())) return "";

        return date.toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit"
        });
    }

    return (
        <div className="messages-page">

            <aside className="messages-sidebar">

                <div className="messages-sidebar-header">
                    <div>
                        <h2>Messages</h2>
                        <p>Your private conversations</p>
                    </div>

                    <button
                        className="messages-refresh"
                        onClick={loadConversations}
                        title="Refresh conversations"
                    >
                        <RefreshCw size={18} />
                    </button>
                </div>

                <div className="messages-connection">
                    <span className={connected ? "connection-dot online" : "connection-dot"} />
                    {connected ? "Connected" : "Connecting..."}
                </div>

                <div className="conversation-list">

                    {loading ? (
                        <p className="messages-empty">Loading conversations...</p>
                    ) : conversations.length === 0 ? (
                        <div className="messages-empty">
                            <MessageCircle size={30} />
                            <p>No conversations yet.</p>
                            <span>Start a conversation with someone from Explore.</span>
                        </div>
                    ) : (
                        conversations.map(person => (
                            <button
                                key={person.userId}
                                className={`conversation-item ${selectedUser?.userId === person.userId
                                    ? "selected"
                                    : ""
                                    }`}
                                onClick={() => loadConversation(person)}
                            >
                                <div className="conversation-avatar">
                                    {(person.username || "U")
                                        .charAt(0)
                                        .toUpperCase()}
                                </div>

                                <div className="conversation-info">
                                    <strong>{person.username}</strong>

                                    <span>Open conversation</span>
                                </div>

                                {person.unreadCount > 0 && (
                                    <span className="unread-badge">
                                        {person.unreadCount}
                                    </span>
                                )}
                            </button>
                        ))
                    )}

                </div>
            </aside>

            <main className="messages-chat">

                {!selectedUser ? (
                    <div className="messages-welcome">
                        <MessageCircle size={55} />
                        <h2>Your messages</h2>
                        <p>Select a conversation to start chatting.</p>
                    </div>
                ) : (
                    <>
                        <div className="chat-header">
                            <div className="conversation-avatar">
                                {(selectedUser.username || "U")
                                    .charAt(0)
                                    .toUpperCase()}
                            </div>

                            <div>
                                <h3>{selectedUser.username}</h3>
                                <span>
                                    {connected
                                        ? "Online connection available"
                                        : "Reconnecting..."}
                                </span>
                            </div>
                        </div>

                        {error && (
                            <div className="messages-error">
                                {error}
                            </div>
                        )}

                        <div className="chat-messages">

                            {messages.length === 0 ? (
                                <div className="chat-empty">
                                    <p>No messages yet.</p>
                                    <span>Send the first message!</span>
                                </div>
                            ) : (
                                messages.map(message => {
                                    const isMine =
                                        message.senderId === user?.userId;

                                    return (
                                        <div
                                            key={message.messageId}
                                            className={`message-row ${isMine ? "my-message" : "their-message"
                                                }`}
                                        >
                                            <div className="message-bubble">
                                                <p>{message.content}</p>

                                                <span>
                                                    {formatTime(message.createdAt)}
                                                    {isMine && (
                                                        <span className="message-read">
                                                            {message.read ? " ✓✓" : " ✓"}
                                                        </span>
                                                    )}
                                                </span>
                                            </div>
                                        </div>
                                    );
                                })

                            )}
                            <div ref={messagesEndRef} />
                        </div>

                        <form
                            className="message-composer"
                            onSubmit={handleSendMessage}
                        >
                            <input
                                type="text"
                                value={content}
                                onChange={event => setContent(event.target.value)}
                                placeholder="Write a message..."
                                maxLength={2000}
                            />

                            <button
                                type="submit"
                                disabled={!content.trim() || !connected || sending}
                                title="Send message"
                            >
                                <Send size={19} />
                            </button>
                        </form>
                    </>
                )}

            </main>
        </div>
    );
}
