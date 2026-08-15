import React,{createContext,useContext,useState,useCallback} from 'react';
import { logout as apiLogout } from '../api/api';
const AuthContext=createContext(null);

// A corrupted 'user' entry (partial write from a crashed tab, manual tampering, a stale shape
// from a previous app version) must never crash every future page load — unlike a one-off render
// error, localStorage persists, so an unguarded JSON.parse here would re-throw on every reload
// forever, with no way for the user to recover short of manually clearing site data. Treat
// anything unparsable the same as "not logged in" and clear it so it doesn't keep tripping.
function readStoredUser(){
    const stored=localStorage.getItem('user');
    if(!stored) return null;
    try{ return JSON.parse(stored); }
    catch{
        localStorage.removeItem('user');
        localStorage.removeItem('token');
        return null;
    }
}

export function AuthProvider({children}){
    const [user,setUser]=useState(readStoredUser);
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
