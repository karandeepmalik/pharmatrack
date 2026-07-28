import React,{createContext,useContext,useState,useCallback} from 'react';
import { logout as apiLogout } from '../api/api';
const AuthContext=createContext(null);
export function AuthProvider({children}){
    const stored=localStorage.getItem('user');
    const [user,setUser]=useState(stored?JSON.parse(stored):null);
    const isAdmin=user?.role==='ADMIN';
    const login=useCallback((userData,token)=>{
        localStorage.setItem('user',JSON.stringify(userData));
        localStorage.setItem('token',token);
        setUser(userData);
    },[]);
    const logout=useCallback(()=>{
        // Clear local state first so the UI (ProtectedRoute reacting to `user`) responds
        // instantly — the server-side cookie clear is best-effort cleanup, not something the
        // signed-out experience should ever block on. Previously this awaited apiLogout() before
        // clearing state, so on slow/high-latency requests Sign Out visibly did nothing until
        // the network round-trip finished.
        localStorage.removeItem('user');
        localStorage.removeItem('token');
        setUser(null);
        apiLogout().catch(()=>{ /* HttpOnly cookie will expire on its own */ });
    },[]);
    return <AuthContext.Provider value={{user,isAdmin,login,logout}}>{children}</AuthContext.Provider>;
}
export const useAuth=()=>useContext(AuthContext);
