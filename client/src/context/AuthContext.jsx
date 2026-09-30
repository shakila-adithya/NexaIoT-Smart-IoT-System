import { createContext, useContext, useEffect, useState } from "react";
import { loginRequest, registerRequest } from "../api/authApi.js";

const AuthContext = createContext(null);

const TOKEN_KEY = "nexaiot_token";
const USER_KEY = "nexaiot_user";

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    try {
      const token = localStorage.getItem(TOKEN_KEY);
      const storedUser = localStorage.getItem(USER_KEY);
      if (token && storedUser) {
        setUser(JSON.parse(storedUser));
      }
    } catch {
      /* ignore corrupted storage */
    } finally {
      setLoading(false);
    }
  }, []);

  const login = async (email, password, remember = true) => {
    const { token, user: loggedInUser } = await loginRequest(email, password);
    const storage = remember ? localStorage : sessionStorage;
    storage.setItem(TOKEN_KEY, token);
    storage.setItem(USER_KEY, JSON.stringify(loggedInUser));
    setUser(loggedInUser);
    return loggedInUser;
  };

  const register = async (payload) => {
    const { token, user: newUser } = await registerRequest(payload);
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(newUser));
    setUser(newUser);
    return newUser;
  };

  const logout = () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, isAuthenticated: !!user }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuthContext() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuthContext must be used within AuthProvider");
  return ctx;
}
