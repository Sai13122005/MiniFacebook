
import { createContext, useContext, useState } from "react";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {

    const [user, setUser] = useState(() => {
        const saved = sessionStorage.getItem("mf_user");
        return saved ? JSON.parse(saved) : null;
    });

    const [sessionToken, setSessionToken] = useState(
        () => sessionStorage.getItem("mf_token") || null
    );

    function login(userData, token) {
        setUser(userData);
        setSessionToken(token);

        sessionStorage.setItem(
            "mf_user",
            JSON.stringify(userData)
        );

        sessionStorage.setItem("mf_token", token);
    }

    function logout() {
        setUser(null);
        setSessionToken(null);

        sessionStorage.removeItem("mf_user");
        sessionStorage.removeItem("mf_token");
    }

    return (
        <AuthContext.Provider
            value={{
                user,
                sessionToken,
                login,
                logout
            }}
        >
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    return useContext(AuthContext);
}
