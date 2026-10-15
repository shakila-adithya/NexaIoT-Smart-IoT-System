import { useCallback, useEffect, useState } from "react";
import { loginRequest, registerRequest } from "../api/authApi.js";
import { getProfileImage } from "../api/userApi.js";
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
  const [profileImageUrl, setProfileImageUrl] = useState(null);

  useEffect(() => {
    const handleUnauthorized = () => {
      clearStoredAuth();
      setUser(null);
      setProfileImageUrl((currentUrl) => {
        if (currentUrl) URL.revokeObjectURL(currentUrl);
        return null;
      });
    };

    window.addEventListener("nexaiot:unauthorized", handleUnauthorized);
    return () => window.removeEventListener("nexaiot:unauthorized", handleUnauthorized);
  }, []);

  useEffect(() => {
    let active = true;

    if (!user?.hasProfileImage) return () => { active = false; };

    if (profileImageUrl) return () => { active = false; };

    getProfileImage()
      .then((blob) => {
        if (!active) return;
        setProfileImageUrl(URL.createObjectURL(blob));
      })
      .catch(() => {
        if (active) setProfileImageUrl(null);
      });

    return () => { active = false; };
  }, [user?.hasProfileImage, profileImageUrl]);

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
    setProfileImageUrl((currentUrl) => {
      if (currentUrl) URL.revokeObjectURL(currentUrl);
      return null;
    });
  };

  const updateUser = useCallback((updatedUser, nextProfileImageUrl) => {
    const storage = sessionStorage.getItem(TOKEN_KEY)
      ? sessionStorage
      : localStorage;
    storage.setItem(USER_KEY, JSON.stringify(updatedUser));
    setUser(updatedUser);

    if (nextProfileImageUrl !== undefined) {
      setProfileImageUrl((currentUrl) => {
        if (currentUrl && currentUrl !== nextProfileImageUrl) URL.revokeObjectURL(currentUrl);
        return nextProfileImageUrl;
      });
    } else if (!updatedUser?.hasProfileImage) {
      setProfileImageUrl((currentUrl) => {
        if (currentUrl) URL.revokeObjectURL(currentUrl);
        return null;
      });
    }
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        loading: false,
        login,
        register,
        logout,
        updateUser,
        profileImageUrl,
        isAuthenticated: !!user,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
