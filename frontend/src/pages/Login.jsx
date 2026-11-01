
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { sendRequest } from "../services/api";
import { useAuth } from "../context/AuthContext";

export default function Login() {

    const navigate = useNavigate();
    const { login } = useAuth();

    const [form, setForm] = useState({
        username: "",
        password: ""
    });

    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    function handleChange(e) {
        setForm({
            ...form,
            [e.target.name]: e.target.value
        });
    }

    async function handleSubmit(e) {
        e.preventDefault();
        setError("");
        setLoading(true);

        try {
            const result = await sendRequest("LOGIN", form);

            if (result.status !== "SUCCESS") {
                setError(result.message);
                return;
            }

            login(result.user, result.sessionToken);
            navigate("/feed");

        } catch (err) {
            setError(err.message || "Unable to connect to server");
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="auth-page">
            <div className="auth-card">

                <div className="brand">
                    <div className="brand-icon">M</div>
                    <h1>MiniFacebook</h1>
                </div>

                <h2>Welcome back</h2>
                <p className="muted">
                    Sign in to connect with your community.
                </p>

                <form onSubmit={handleSubmit}>

                    <label>Username</label>
                    <input
                        name="username"
                        value={form.username}
                        onChange={handleChange}
                        placeholder="Enter your username"
                        required
                    />

                    <label>Password</label>
                    <input
                        name="password"
                        type="password"
                        value={form.password}
                        onChange={handleChange}
                        placeholder="Enter your password"
                        required
                    />

                    {error && (
                        <p className="error-message">{error}</p>
                    )}

                    <button type="submit" disabled={loading}>
                        {loading ? "Signing in..." : "Sign In"}
                    </button>

                </form>

                <p className="auth-footer">
                    Don't have an account?{" "}
                    <Link to="/register">Create account</Link>
                </p>

            </div>
        </div>
    );
}
