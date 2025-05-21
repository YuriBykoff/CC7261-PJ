"use client"

import { useEffect, useState } from "react"
import { Check, ChevronsUpDown, Loader2, UserCircle2 } from "lucide-react"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
} from "@/components/ui/command"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"
import { toast } from "sonner"
import { useUser } from "@/context/user-context"

interface User {
  id: string
  name: string
}

export function UserSelect() {
  const [open, setOpen] = useState(false)
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(false)

  const { selectedUser, setSelectedUser } = useUser()

  useEffect(() => {
    const fetchUsers = async () => {
      setLoading(true)
      try {
        const response = await fetch("/api/spring/users")
        if (!response.ok) throw new Error("Falha ao carregar usuários")
        const data = await response.json()
        setUsers(data)
      } catch (error) {
        console.error("Erro ao buscar usuários:", error)
        toast.error("Não foi possível carregar a lista de usuários")
      } finally {
        setLoading(false)
      }
    }

    fetchUsers()
  }, [])

  const handleSelectUser = (user: User) => {
    setSelectedUser(user)
    setOpen(false)
    toast.success(`Usuário ${user.name} selecionado`)
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className="w-full justify-between"
          disabled={loading}
        >
          {loading ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Carregando...
            </>
          ) : selectedUser ? (
            <>
              <UserCircle2 className="mr-2 h-4 w-4" />
              {selectedUser.name}
            </>
          ) : (
            "Selecionar usuário..."
          )}
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-full p-0">
        <Command>
          <CommandInput placeholder="Buscar usuário..." />
          <CommandEmpty>Nenhum usuário encontrado.</CommandEmpty>
          <CommandGroup>
            {users.map((user) => (
              <CommandItem
                key={user.id}
                onSelect={() => handleSelectUser(user)}
                className="cursor-pointer"
              >
                <Check
                  className={cn(
                    "mr-2 h-4 w-4",
                    selectedUser?.id === user.id ? "opacity-100" : "opacity-0"
                  )}
                />
                {user.name}
              </CommandItem>
            ))}
          </CommandGroup>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
