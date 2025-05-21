"use client"

import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { UserMinus, UserPlus, Users, ChevronsUpDown } from "lucide-react"
import { toast } from "sonner"
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
import { useUser } from "@/context/user-context"

interface User {
  id: string
  name: string
}

export function UserFollowManager() {
  const [followers, setFollowers] = useState<User[]>([])
  const [following, setFollowing] = useState<User[]>([])
  const [allUsers, setAllUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(false)
  const [openPopover, setOpenPopover] = useState(false)
  const { selectedUser } = useUser()
  const currentUserId = selectedUser?.id || null

  // Buscar todos os usuários
  const fetchAllUsers = async () => {
    try {
      const response = await fetch("/api/spring/users")
      if (!response.ok) throw new Error("Falha ao carregar usuários")
      const data = await response.json()
      setAllUsers(data)
    } catch (error) {
      toast.error("Erro ao carregar lista de usuários")
      console.error("Erro ao carregar lista de usuários:", error)
    }
  }

  // Buscar seguidores e seguindo
  const fetchFollowData = async () => {
    if (!currentUserId) return

    setLoading(true)
    try {
      const [followersResponse, followingResponse] = await Promise.all([
        fetch(`/api/spring/follow/get-followers?userId=${currentUserId}`),
        fetch(`/api/spring/follow/get-following?userId=${currentUserId}`),
      ])

      if (!followersResponse.ok) {
        const errorData = await followersResponse.json().catch(() => ({}))
        throw new Error(errorData.error || "Falha ao carregar seguidores")
      }
      const followersData = await followersResponse.json()
      setFollowers(followersData)

      if (!followingResponse.ok) {
        const errorData = await followingResponse.json().catch(() => ({}))
        throw new Error(errorData.error || "Falha ao carregar quem você segue")
      }
      const followingData = await followingResponse.json()
      setFollowing(followingData)
    } catch (error) {
      const errorMsg =
        error instanceof Error
          ? error.message
          : "Erro desconhecido ao carregar dados de seguidores/seguindo"
      toast.error(errorMsg)
      console.error("Erro ao carregar dados de seguidores/seguindo:", error)
    } finally {
      setLoading(false)
    }
  }

  // Follow user
  const handleFollow = async (targetUserId: string) => {
    if (!currentUserId) {
      toast.error("Selecione um usuário primeiro")
      return
    }

    try {
      const response = await fetch("/api/spring/follow/post-follow", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ currentUserId, targetUserId }),
      })

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.error || "Falha ao seguir usuário")
      }
      toast.success("Usuário seguido com sucesso")
      setOpenPopover(false)
      fetchFollowData()
    } catch (error) {
      const errorMsg =
        error instanceof Error
          ? error.message
          : "Erro desconhecido ao seguir usuário"
      toast.error(errorMsg)
      console.error("Erro ao seguir usuário:", error)
    }
  }

  // Unfollow user
  const handleUnfollow = async (targetUserId: string) => {
    if (!currentUserId) return

    try {
      const response = await fetch("/api/spring/follow/remove-follow", {
        method: "DELETE",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          actionType: "unfollow",
          currentUserId,
          targetUserId,
        }),
      })
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.error || "Falha ao deixar de seguir usuário")
      }
      toast.success("Deixou de seguir o usuário")
      fetchFollowData()
    } catch (error) {
      const errorMsg =
        error instanceof Error
          ? error.message
          : "Erro desconhecido ao deixar de seguir"
      toast.error(errorMsg)
      console.error("Erro ao deixar de seguir:", error)
    }
  }

  // Remover seguidor
  const handleRemoveFollower = async (userToRemoveId: string) => {
    if (!currentUserId) return

    try {
      const response = await fetch("/api/spring/follow/remove-follow", {
        method: "DELETE",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          actionType: "removeFollower",
          followerId: userToRemoveId,
          currentUserId: currentUserId,
        }),
      })
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.error || "Falha ao remover seguidor")
      }
      toast.success("Seguidor removido com sucesso")
      fetchFollowData()
    } catch (error) {
      const errorMsg =
        error instanceof Error
          ? error.message
          : "Erro desconhecido ao remover seguidor"
      toast.error(errorMsg)
      console.error("Erro ao remover seguidor:", error)
    }
  }

  useEffect(() => {
    if (currentUserId) {
      fetchFollowData()
      fetchAllUsers()
    } else {
      setFollowers([])
      setFollowing([])
      setAllUsers([])
    }
  }, [currentUserId])

  if (!currentUserId) {
    return (
      <div className="flex items-center justify-center h-[600px] text-muted-foreground">
        Selecione um usuário para gerenciar seguidores
      </div>
    )
  }

  return (
    <div className="space-y-12">
      <Popover open={openPopover} onOpenChange={setOpenPopover}>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            className="w-full bg-background hover:bg-muted/50 border-dashed"
            disabled={loading || !allUsers.length}
          >
            <UserPlus className="mr-2 h-4 w-4" />
            Seguir Novo Usuário
            <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-full p-0">
          <Command>
            <CommandInput placeholder="Buscar usuário..." />
            <CommandEmpty>Nenhum usuário encontrado.</CommandEmpty>
            <CommandGroup>
              {allUsers
                .filter(
                  (user) =>
                    user.id !== currentUserId &&
                    !following.some((f) => f.id === user.id)
                )
                .map((user) => (
                  <CommandItem
                    key={user.id}
                    onSelect={() => handleFollow(user.id)}
                    className="cursor-pointer"
                  >
                    <UserPlus className="mr-2 h-4 w-4 opacity-50" />
                    {user.name}
                  </CommandItem>
                ))}
            </CommandGroup>
          </Command>
        </PopoverContent>
      </Popover>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        <div className="space-y-4">
          <div className="flex items-center gap-2 px-1">
            <Users className="h-5 w-5 text-primary" />
            <label className="text-base font-medium">
              Seguindo ({following.length})
            </label>
          </div>
          <ScrollArea className="h-[500px] rounded-lg border bg-muted/5">
            <div className="p-4 space-y-2">
              {loading && following.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-4">
                  Carregando...
                </p>
              ) : following.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-4">
                  Não está seguindo ninguém
                </p>
              ) : (
                following.map((followed) => (
                  <div
                    key={followed.id}
                    className="flex items-center justify-between rounded-md p-2 hover:bg-muted/50 group"
                  >
                    <span className="text-sm font-medium">{followed.name}</span>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleUnfollow(followed.id)}
                      className="opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      <UserMinus className="h-4 w-4" />
                    </Button>
                  </div>
                ))
              )}
            </div>
          </ScrollArea>
        </div>

        <div className="space-y-4">
          <div className="flex items-center gap-2 px-1">
            <Users className="h-5 w-5 text-primary" />
            <label className="text-base font-medium">
              Seguidores ({followers.length})
            </label>
          </div>
          <ScrollArea className="h-[500px] rounded-lg border bg-muted/5">
            <div className="p-4 space-y-2">
              {loading && followers.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-4">
                  Carregando...
                </p>
              ) : followers.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-4">
                  Nenhum seguidor ainda
                </p>
              ) : (
                followers.map((follower) => (
                  <div
                    key={follower.id}
                    className="flex items-center justify-between rounded-md p-2 hover:bg-muted/50 group"
                  >
                    <span className="text-sm font-medium">{follower.name}</span>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleRemoveFollower(follower.id)}
                      className="opacity-0 group-hover:opacity-100 transition-opacity text-destructive hover:text-destructive hover:bg-destructive/10"
                    >
                      <UserMinus className="h-4 w-4" />
                    </Button>
                  </div>
                ))
              )}
            </div>
          </ScrollArea>
        </div>
      </div>
    </div>
  )
}
