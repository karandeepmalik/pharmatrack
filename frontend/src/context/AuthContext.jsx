import React,{createContext,useContext,useState,useCallback} from 'react';
const AuthContext=createContext(null);
export function AuthProvider({children}){
    const stored=localStorage.getItem('user');
    const [user,setUser]=useState(stored?JSON.parse(stored):null);
    const isAdmin=user?.role==='ADMIN';
    const login=useCallback((userData,token)=>{ localStorage.setItem('user',JSON.stringify(userData)); localStorage.setItem('token',token); setUser(userData); },[]);
    const logout=useCallback(()=>{ localStorage.removeItem('user'); localStorage.removeItem('token'); setUser(null); },[]);
    return <AuthContext.Provider value={{user,isAdmin,login,logout}}>{children}</AuthContext.Provider>;
}
export const useAuth=()=>useContext(AuthContext);
