import { createContext, useContext, useEffect, useState } from 'react';
import { auth, getSession, setSession, setSessionListener } from './api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setState] = useState(getSession());

  useEffect(() => {
    setSessionListener(setState);
    return () => setSessionListener(() => {});
  }, []);

  const value = {
    user: session?.user || null,
    signedIn: Boolean(session?.accessToken),
    login: async (email, password) => setSession(await auth.login(email, password)),
    register: async (data) => setSession(await auth.register(data)),
    logout: () => setSession(null),
    updateUser: (user) => setSession({ ...getSession(), user }),
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
