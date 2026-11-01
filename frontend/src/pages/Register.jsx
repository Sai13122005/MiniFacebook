
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { sendRequest } from "../services/api";

export default function Register() {

    const navigate = useNavigate();

    const [form, setForm] = useState({
        username: "",
        password: ""
    });

    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
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
        setSuccess("");
        setLoading(true);

        try {
            const result = await sendRequest("REGISTER", form);

            if (result.status !== "SUCCESS") {
                setError(result.message);
                return;
            }

            setSuccess("Account created successfully!");

            setTimeout(() => navigate("/login"), 1200);

        } catch (err) {
            setError(err.message || "Registration failed");
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

                <h2>Create your account</h2>

                <p className="muted">
                    Join the community and start connecting.
                </p>

                <form onSubmit={handleSubmit}>

                    <label>Username</label>

                    <input
                        name="username"
                        value={form.username}
                        onChange={handleChange}
                        placeholder="Choose a username"
                        required
                    />

                    <label>Password</label>

                    <input
                        name="password"
                        type="password"
                        value={form.password}
                        onChange={handleChange}
                        placeholder="Minimum 8 characters"
                        minLength={8}
                        required
                    />

                    {error && (
                        <p className="error-message">{error}</p>
                    )}

                    {success && (
                        <p className="success-message">{success}</p>
                    )}

                    <button type="submit" disabled={loading}>
                        {loading ? "Creating account..." : "Create Account"}
                    </button>

                </form>

                <p className="auth-footer">
                    Already have an account?{" "}
                    <Link to="/login">Sign in</Link>
                </p>

            </div>
        </div>
    );
}
