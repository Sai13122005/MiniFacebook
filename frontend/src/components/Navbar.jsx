
import { NavLink, useNavigate } from "react-router-dom";
import {
    Home,
    Compass,
    Bell,
    UserRound,
    LogOut,
    MessageCircle,
} from "lucide-react";

import { useEffect, useState, useCallback } from "react";
import { useAuth } from "../context/AuthContext";
import { sendRequest } from "../services/api";

export default function Navbar() {
    const { user, logout, sessionToken } = useAuth();

    const [unreadMessages, setUnreadMessages] = useState(0);

    const navigate = useNavigate();

    function handleLogout() {
        logout();
        navigate("/login");
    }

    // Fetch the total unread message count.
    const loadUnreadCount = useCallback(async () => {
        if (!sessionToken) {
            setUnreadMessages(0);
            return;
        }

        try {
            const result = await sendRequest(
                "GET_CONVERSATIONS",
                { sessionToken }
            );

            if (result.status === "SUCCESS") {
                const total = (result.conversations || []).reduce(
                    (sum, conversation) =>
                        sum + (conversation.unreadCount || 0),
                    0
                );

                setUnreadMessages(total);
            }
        } catch (error) {
            console.error(
                "Unable to load unread message count:",
                error
            );
        }
    }, [sessionToken]);

    // Initial fetch and periodic refresh.
    useEffect(() => {
        if (!sessionToken) {
            setUnreadMessages(0);
            return;
        }

        let active = true;

        async function refreshUnreadCount() {
            try {
                const result = await sendRequest(
                    "GET_CONVERSATIONS",
                    { sessionToken }
                );

                if (result.status === "SUCCESS" && active) {
                    const total = (result.conversations || []).reduce(
                        (sum, conversation) =>
                            sum + (conversation.unreadCount || 0),
                        0
                    );

                    setUnreadMessages(total);
                }
            } catch (error) {
                console.error(
                    "Unable to load unread message count:",
                    error
                );
            }
        }

        refreshUnreadCount();

        const interval = setInterval(
            refreshUnreadCount,
            15000
        );

        return () => {
            active = false;
            clearInterval(interval);
        };
    }, [sessionToken]);

    // Immediately refresh when a conversation is opened.
    useEffect(() => {
        function handleMessagesRead() {
            loadUnreadCount();
        }

        window.addEventListener(
            "messages-read",
            handleMessagesRead
        );

        return () => {
            window.removeEventListener(
                "messages-read",
                handleMessagesRead
            );
        };
    }, [loadUnreadCount]);

    const links = [
        { path: "/feed", label: "Home", icon: Home },
        { path: "/explore", label: "Explore", icon: Compass },
        {
            path: "/messages",
            label: "Messages",
            icon: MessageCircle,
            badge: unreadMessages,
        },
        { path: "/notifications", label: "Notifications", icon: Bell },
        { path: "/profile", label: "Profile", icon: UserRound },
    ];

    return (
        <header className="app-navbar">
            <div className="navbar-left">
                <NavLink to="/feed" className="navbar-brand">
                    MiniFacebook
                </NavLink>
            </div>

            <nav className="navbar-links">
                {links.map(({ path, label, icon: Icon, badge }) => (
                    <NavLink
                        key={path}
                        to={path}
                        className={({ isActive }) =>
                            `navbar-link ${isActive ? "active" : ""}`
                        }
                        title={label}
                    >
                        <span className="navbar-icon-wrapper">
                            <Icon size={23} strokeWidth={2} />

                            {badge > 0 && (
                                <span className="navbar-message-badge">
                                    {badge > 99 ? "99+" : badge}
                                </span>
                            )}
                        </span>

                        <span>{label}</span>
                    </NavLink>
                ))}
            </nav>

            <div className="navbar-right">
                <div className="navbar-user-avatar">
                    {(user?.username || "U").charAt(0).toUpperCase()}
                </div>

                <span className="navbar-welcome">
                    {user?.username || "User"}
                </span>

                <button
                    type="button"
                    className="navbar-logout"
                    onClick={handleLogout}
                    title="Logout"
                >
                    <LogOut size={18} />
                    <span>Logout</span>
                </button>
            </div>
        </header>
    );
}
