
const WS_URL = import.meta.env.VITE_WS_URL;

export function connectMessageSocket({
    onMessage,
    onConnected,
    onDisconnected,
    onError
}) {
    const token = sessionStorage.getItem("mf_token");

    if (!token) {
        throw new Error("Please login before connecting to messaging");
    }

    let socket = null;
    let reconnectTimer = null;
    let reconnectAttempts = 0;
    let manuallyDisconnected = false;
    let authenticated = false;

    const MAX_RECONNECT_DELAY = 30000;
    const INITIAL_RECONNECT_DELAY = 1000;

    function scheduleReconnect() {
        if (manuallyDisconnected || reconnectTimer) return;

        const delay = Math.min(
            INITIAL_RECONNECT_DELAY * Math.pow(2, reconnectAttempts),
            MAX_RECONNECT_DELAY
        );

        reconnectAttempts++;

        console.log(
            `Reconnecting to messaging server in ${delay / 1000}s`
        );

        reconnectTimer = setTimeout(() => {
            reconnectTimer = null;
            connect();
        }, delay);
    }

    function connect() {
        if (manuallyDisconnected) return;

        try {
            const currentSocket = new WebSocket(WS_URL);
            socket = currentSocket;

            currentSocket.onopen = () => {
                if (manuallyDisconnected) {
                    currentSocket.close();
                    return;
                }

                currentSocket.send(JSON.stringify({
                    command: "AUTH",
                    sessionToken: token
                }));
            };

            currentSocket.onmessage = (event) => {
                if (currentSocket !== socket) return;

                try {
                    const data = JSON.parse(event.data);

                    switch (data.event) {
                        case "AUTH_SUCCESS":
                            authenticated = true;
                            reconnectAttempts = 0;
                            onConnected?.(data);
                            break;

                        case "AUTH_REQUIRED":
                            break;

                        case "NEW_MESSAGE":
                        case "MESSAGE_SENT":
                        case "MESSAGE_READ":
                        case "READ_STATUS_UPDATED":
                            onMessage?.(data);
                            break;

                        case "ERROR":
                            onError?.(data.message);
                            break;

                        default:
                            console.log("Unknown WebSocket event:", data);
                    }
                } catch (error) {
                    console.error("Invalid WebSocket response:", error);
                }
            };

            currentSocket.onclose = () => {
                if (currentSocket !== socket) return;

                const wasAuthenticated = authenticated;
                authenticated = false;

                if (wasAuthenticated) {
                    onDisconnected?.();
                }

                scheduleReconnect();
            };

            currentSocket.onerror = () => {
                if (currentSocket !== socket) return;

                onError?.("WebSocket connection error");
            };

        } catch (error) {
            console.error("Unable to create WebSocket:", error);
            scheduleReconnect();
        }
    }

    connect();

    return {
        sendMessage(receiverId, content) {
            if (
                !authenticated ||
                socket?.readyState !== WebSocket.OPEN
            ) {
                throw new Error(
                    "Messaging is reconnecting. Please try again."
                );
            }

            socket.send(JSON.stringify({
                command: "SEND_MESSAGE",
                receiverId,
                content
            }));
        },

        markMessagesRead(otherUserId) {
            if (
                !authenticated ||
                socket?.readyState !== WebSocket.OPEN
            ) {
                return;
            }

            socket.send(JSON.stringify({
                command: "MARK_MESSAGE_READ",
                otherUserId
            }));
        },

        disconnect() {
            manuallyDisconnected = true;
            authenticated = false;

            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
                reconnectTimer = null;
            }

            if (socket) {
                const currentSocket = socket;

                // Prevent cleanup from triggering reconnection.
                currentSocket.onclose = null;
                currentSocket.onerror = null;
                currentSocket.onmessage = null;

                // Close only if the connection is still active.
                if (
                    currentSocket.readyState === WebSocket.CONNECTING ||
                    currentSocket.readyState === WebSocket.OPEN
                ) {
                    currentSocket.close();
                }

                socket = null;
            }
        },

        get readyState() {
            return authenticated && socket?.readyState === WebSocket.OPEN
                ? WebSocket.OPEN
                : WebSocket.CONNECTING;
        },

        get isConnected() {
            return authenticated;
        }
    };
}
