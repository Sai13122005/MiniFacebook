
import { useCallback, useEffect, useState } from "react";
import {
    Heart,
    MessageCircle,
    UserPlus,
    Bell,
    RefreshCw,
    CheckCheck,
} from "lucide-react";
import { sendRequest } from "../services/api";
import { useAuth } from "../context/AuthContext";

export default function Notifications() {
    const { sessionToken } = useAuth();

    const [notifications, setNotifications] = useState([]);
    const [unreadCount, setUnreadCount] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [markingIds, setMarkingIds] = useState([]);

    const loadNotifications = useCallback(async () => {
        try {
            setLoading(true);
            setError("");

            const response = await sendRequest("GET_NOTIFICATIONS", {
                sessionToken,
            });

            if (response.status !== "SUCCESS") {
                throw new Error(
                    response.message || "Failed to load notifications"
                );
            }

            setNotifications(response.notifications || []);
            setUnreadCount(response.unreadCount || 0);
        } catch (err) {
            setError(err.message || "Unable to load notifications");
        } finally {
            setLoading(false);
        }
    }, [sessionToken]);

    useEffect(() => {
        loadNotifications();
    }, [loadNotifications]);

    const markAsRead = async (notification) => {
        if (notification.read || markingIds.includes(notification.notificationId)) {
            return;
        }

        const notificationId = notification.notificationId;

        try {
            setMarkingIds((previous) => [...previous, notificationId]);

            const response = await sendRequest("MARK_NOTIFICATION_READ", {
                sessionToken,
                notificationId,
            });

            if (response.status !== "SUCCESS") {
                throw new Error(
                    response.message || "Unable to mark notification as read"
                );
            }

            setNotifications((previous) =>
                previous.map((item) =>
                    item.notificationId === notificationId
                        ? { ...item, read: true }
                        : item
                )
            );

            setUnreadCount((previous) => Math.max(0, previous - 1));
        } catch (err) {
            setError(err.message || "Unable to update notification");
        } finally {
            setMarkingIds((previous) =>
                previous.filter((id) => id !== notificationId)
            );
        }
    };

    const getNotificationIcon = (type) => {
        switch (type?.toUpperCase()) {
            case "LIKE":
                return <Heart size={19} />;
            case "COMMENT":
                return <MessageCircle size={19} />;
            case "FOLLOW":
                return <UserPlus size={19} />;
            default:
                return <Bell size={19} />;
        }
    };

    const formatTime = (dateString) => {
        if (!dateString) return "";

        const date = new Date(dateString);

        if (Number.isNaN(date.getTime())) return "";

        return date.toLocaleString([], {
            dateStyle: "medium",
            timeStyle: "short",
        });
    };

    return (
        <main className="notifications-page">
            <section className="notifications-header">
                <div>
                    <span className="notifications-eyebrow">YOUR ACTIVITY</span>
                    <h1>Notifications</h1>
                    <p>Stay updated with what's happening in your network.</p>
                </div>

                <div className="notifications-header-icon">
                    <Bell size={27} />
                </div>
            </section>

            <section className="notifications-summary">
                <div>
                    <span>All notifications</span>
                    <strong>{notifications.length}</strong>
                </div>

                <div>
                    <span>Unread</span>
                    <strong>{unreadCount}</strong>
                </div>

                <button
                    className="notifications-refresh"
                    onClick={loadNotifications}
                    disabled={loading}
                >
                    <RefreshCw size={16} />
                    Refresh
                </button>
            </section>

            {error && (
                <div className="notifications-error">
                    {error}
                    <button onClick={loadNotifications}>Try again</button>
                </div>
            )}

            {loading ? (
                <div className="notifications-state">
                    <RefreshCw className="spin" size={25} />
                    <p>Loading notifications...</p>
                </div>
            ) : notifications.length === 0 ? (
                <div className="notifications-state">
                    <Bell size={36} />
                    <h3>You're all caught up!</h3>
                    <p>New likes, comments, and followers will appear here.</p>
                </div>
            ) : (
                <section className="notifications-list">
                    {notifications.map((notification) => (
                        <article
                            key={notification.notificationId}
                            className={`notification-card ${
                                notification.read ? "read" : "unread"
                            }`}
                        >
                            <div className={`notification-icon ${notification.type?.toLowerCase()}`}>
                                {getNotificationIcon(notification.type)}
                            </div>

                            <div className="notification-content">
                                <p>{notification.message}</p>

                                <span className="notification-time">
                                    {formatTime(notification.createdAt)}
                                </span>
                            </div>

                            {!notification.read && (
                                <button
                                    className="notification-read-btn"
                                    onClick={() => markAsRead(notification)}
                                    disabled={markingIds.includes(
                                        notification.notificationId
                                    )}
                                    title="Mark as read"
                                >
                                    <CheckCheck size={17} />
                                    <span>Mark read</span>
                                </button>
                            )}

                            {!notification.read && (
                                <span className="notification-unread-dot" />
                            )}
                        </article>
                    ))}
                </section>
            )}
        </main>
    );
}
    