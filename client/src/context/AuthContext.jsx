import { useState } from "react";
import { loginRequest, registerRequest } from "../api/authApi.js";
import AuthContext from "./authContext.js";

const TOKEN_KEY = "nexaiot_token";
const USER_KEY = "nexaiot_user";

function clearStoredAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);

  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_KEY);
}

function getStoredUser() {
  try {
    const sessionToken = sessionStorage.getItem(TOKEN_KEY);
    const sessionUser = sessionStorage.getItem(USER_KEY);

    if (sessionToken && sessionUser) {
      return JSON.parse(sessionUser);
    }

    const localToken = localStorage.getItem(TOKEN_KEY);
    const localUser = localStorage.getItem(USER_KEY);

    if (localToken && localUser) {
      return JSON.parse(localUser);
    }

    return null;
  } catch {
    clearStoredAuth();
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => getStoredUser());

  const login = async (email, password, remember = true) => {
    const { token, user: loggedInUser } = await loginRequest(
      email,
      password
    );

    clearStoredAuth();

    const storage = remember ? localStorage : sessionStorage;

    storage.setItem(TOKEN_KEY, token);
    storage.setItem(USER_KEY, JSON.stringify(loggedInUser));

    setUser(loggedInUser);

    return loggedInUser;
  };

  const register = async (payload) => {
    const { token, user: newUser } = await registerRequest(payload);

    clearStoredAuth();

    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(newUser));

    setUser(newUser);

    return newUser;
  };

  const logout = () => {
    clearStoredAuth();
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        loading: false,
        login,
        register,
        logout,
        isAuthenticated: !!user,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}