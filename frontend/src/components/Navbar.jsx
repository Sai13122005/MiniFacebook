
import { NavLink, useNavigate } from "react-router-dom";
import {
    Home,
    Compass,
    Bell,
    UserRound,
    LogOut,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";

export default function Navbar() {
    const { user, logout } = useAuth();
    const navigate = useNavigate();

    function handleLogout() {
        logout();
        navigate("/login");
    }

    const links = [
        { path: "/feed", label: "Home", icon: Home },
        { path: "/explore", label: "Explore", icon: Compass },
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
                {links.map(({ path, label, icon: Icon }) => (
                    <NavLink
                        key={path}
                        to={path}
                        className={({ isActive }) =>
                            `navbar-link ${isActive ? "active" : ""}`
                        }
                        title={label}
                    >
                        <Icon size={23} strokeWidth={2} />
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
