"use client"

import React, {
  createContext,
  useState,
  useContext,
  ReactNode,
  useEffect,
} from "react"

interface User {
  id: string
  name: string
}

interface UserContextType {
  selectedUser: User | null
  setSelectedUser: (user: User | null) => void
}

const UserContext = createContext<UserContextType | undefined>(undefined)

export const UserProvider = ({ children }: { children: ReactNode }) => {
  const [selectedUser, setSelectedUserState] = useState<User | null>(null)

  // Carregar do localStorage na inicialização
  useEffect(() => {
    const savedUser = localStorage.getItem("selectedUser")
    if (savedUser) {
      try {
        setSelectedUserState(JSON.parse(savedUser))
      } catch (e) {
        console.error("Failed to parse saved user from localStorage", e)
        localStorage.removeItem("selectedUser") // Limpar item inválido
      }
    }
  }, [])

  const setSelectedUser = (user: User | null) => {
    setSelectedUserState(user)
    if (user) {
      localStorage.setItem("selectedUser", JSON.stringify(user))
    } else {
      localStorage.removeItem("selectedUser")
    }
  }

  return (
    <UserContext.Provider value={{ selectedUser, setSelectedUser }}>
      {children}
    </UserContext.Provider>
  )
}

export const useUser = (): UserContextType => {
  const context = useContext(UserContext)
  if (context === undefined) {
    throw new Error("useUser must be used within a UserProvider")
  }
  return context
}
