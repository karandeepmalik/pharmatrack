import React,{createContext,useContext,useState,useCallback} from 'react';
import { logout as apiLogout } from '../api/api';
const AuthContext=createContext(null);
export function AuthProvider({children}){
    const stored=localStorage.getItem('user');
    const [user,setUser]=useState(stored?JSON.parse(stored):null);
    const isAdmin=user?.role==='ADMIN';
    const login=useCallback((userData)=>{ localStorage.setItem('user',JSON.stringify(userData)); setUser(userData); },[]);
    const logout=useCallback(async ()=>{
        try{ await apiLogout(); }catch{ /* cookie will expire on its own */ }
        localStorage.removeItem('user'); setUser(null);
    },[]);
    return <AuthContext.Provider value={{user,isAdmin,login,logout}}>{children}</AuthContext.Provider>;
}
export const useAuth=()=>useContext(AuthContext);
