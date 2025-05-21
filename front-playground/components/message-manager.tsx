"use client"

import { useState, useEffect, useRef } from "react"
import { useUser } from "@/context/user-context"
import { User } from "@/types/user"
import { Message } from "@/types/message"
import { toast } from "sonner"

// UI Components
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
} from "@/components/ui/command"

// Icons
import {
  Loader2,
  Send,
  MessageSquare,
  Users,
  ChevronsUpDown,
} from "lucide-react"

export function MessageManager() {
  const { selectedUser } = useUser()
  const currentUserId = selectedUser?.id

  const [users, setUsers] = useState<User[]>([])
  const [chatUser, setChatUser] = useState<User | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [inputMessage, setInputMessage] = useState("")
  const [isLoading, setIsLoading] = useState(false)
  const [isSending, setIsSending] = useState(false)
  const [open, setOpen] = useState(false)

  const messagesEndRef = useRef<HTMLDivElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const loadUsers = async () => {
      try {
        const response = await fetch("/api/spring/users")
        if (!response.ok) throw new Error("Falha ao carregar usuários")

        const data = await response.json()
        if (Array.isArray(data)) {
          setUsers(data)
        }
      } catch (error) {
        console.error("Erro ao carregar usuários:", error)
        toast.error("Não foi possível carregar a lista de usuários")
      }
    }

    loadUsers()
  }, [])

  useEffect(() => {
    if (!currentUserId || !chatUser) {
      setMessages([])
      return
    }

    const loadMessages = async () => {
      setIsLoading(true)
      try {
        // Chama a nova API Route do Next.js
        const response = await fetch(
          `/api/spring/message/get-message?userId=${currentUserId}&otherUserId=${chatUser.id}`
        )

        if (!response.ok) {
          const errorData = await response.json().catch(() => ({}))
          throw new Error(errorData.error || "Falha ao carregar mensagens")
        }

        const data = await response.json()

        let messageList: Message[] = []
        if (data && Array.isArray(data.content)) {
          messageList = data.content // Para respostas paginadas da API Spring
        } else if (Array.isArray(data)) {
          messageList = data // Caso a API Route retorne diretamente o array
        }

        messageList.sort(
          (a, b) => new Date(a.sentAt).getTime() - new Date(b.sentAt).getTime()
        )

        setMessages(messageList)
      } catch (error) {
        const errorMsg =
          error instanceof Error
            ? error.message
            : "Erro desconhecido ao carregar mensagens"
        console.error("Erro ao carregar mensagens:", error)
        toast.error(errorMsg)
        setMessages([])
      } finally {
        setIsLoading(false)
      }
    }

    loadMessages()
  }, [currentUserId, chatUser])

  useEffect(() => {
    requestAnimationFrame(() => {
      if (messagesEndRef.current) {
        messagesEndRef.current.scrollIntoView({ behavior: "smooth" })
      }
    })
  }, [messages])

  // Select a user to chat with
  const selectChatUser = (user: User) => {
    setChatUser(user)
    setOpen(false)
  }

  // Send a message
  const sendMessage = async (e: React.FormEvent) => {
    e.preventDefault()

    if (!inputMessage.trim() || !currentUserId || !chatUser || isSending) {
      return
    }

    setIsSending(true)
    const messageContent = inputMessage.trim()
    setInputMessage("")

    try {
      // Chama a nova API Route do Next.js
      const response = await fetch("/api/spring/message/post-message", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          senderId: currentUserId,
          receiverId: chatUser.id,
          content: messageContent,
        }),
      })

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.error || "Falha ao enviar mensagem")
      }

      const newMessage = await response.json()
      setMessages((prev) => [...prev, newMessage])
    } catch (error) {
      const errorMsg =
        error instanceof Error
          ? error.message
          : "Erro desconhecido ao enviar mensagem"
      console.error("Erro ao enviar mensagem:", error)
      toast.error(errorMsg)
      setInputMessage(messageContent) 
    } finally {
      setIsSending(false)
    }
  }

  if (!currentUserId) {
    return (
      <div className="flex items-center justify-center h-[80vh] text-muted-foreground">
        Selecione um usuário na barra lateral para começar
      </div>
    )
  }

  return (
    <div
      ref={containerRef}
      className="flex flex-col h-[calc(100vh-8rem)] border rounded-md shadow-sm overflow-hidden"
    >
      <div className="flex items-center justify-between p-3 border-b bg-muted/20">
        <div className="flex items-center gap-2">
          <MessageSquare className="w-5 h-5 text-primary" />
          <h2 className="font-medium">
            Conversa com:{" "}
            {chatUser ? (
              <span className="font-bold">{chatUser.name}</span>
            ) : (
              <span className="italic text-muted-foreground">
                Ninguém selecionado
              </span>
            )}
          </h2>
        </div>

        <Popover open={open} onOpenChange={setOpen}>
          <PopoverTrigger asChild>
            <Button variant="outline" className="gap-1">
              <span className="hidden sm:inline">
                {chatUser ? "Trocar usuário" : "Selecionar usuário"}
              </span>
              <ChevronsUpDown className="h-4 w-4 opacity-50" />
            </Button>
          </PopoverTrigger>
          <PopoverContent className="p-0" align="end">
            <Command>
              <CommandInput placeholder="Buscar usuário..." />
              <CommandEmpty>Nenhum usuário encontrado</CommandEmpty>
              <CommandGroup>
                {users
                  .filter((user) => user.id !== currentUserId)
                  .map((user) => (
                    <CommandItem
                      key={user.id}
                      onSelect={() => selectChatUser(user)}
                      className="flex items-center gap-2 cursor-pointer"
                    >
                      <Users className="w-4 h-4 text-muted-foreground" />
                      {user.name}
                    </CommandItem>
                  ))}
              </CommandGroup>
            </Command>
          </PopoverContent>
        </Popover>
      </div>

      {/* Messages area */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3 bg-muted/5">
        {!chatUser ? (
          <div className="flex items-center justify-center h-full">
            <p className="text-muted-foreground">
              Selecione um usuário para iniciar uma conversa
            </p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center h-full">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : messages.length === 0 ? (
          <div className="flex items-center justify-center h-full">
            <p className="text-muted-foreground">
              Nenhuma mensagem. Inicie a conversa!
            </p>
          </div>
        ) : (
          <>
            {messages.map((message) => (
              <div
                key={message.id}
                className={`flex ${
                  message.senderId === currentUserId
                    ? "justify-end"
                    : "justify-start"
                }`}
              >
                <div
                  className={`p-3 rounded-lg max-w-[75%] break-words ${
                    message.senderId === currentUserId
                      ? "bg-primary text-primary-foreground"
                      : "bg-card border shadow-sm"
                  }`}
                >
                  <p className="text-sm">{message.content}</p>
                  <p
                    className={`text-xs mt-1 ${
                      message.senderId === currentUserId
                        ? "text-primary-foreground/75"
                        : "text-muted-foreground"
                    }`}
                  >
                    {new Date(message.sentAt).toLocaleTimeString([], {
                      hour: "2-digit",
                      minute: "2-digit",
                    })}
                  </p>
                </div>
              </div>
            ))}
            <div ref={messagesEndRef} />
          </>
        )}
      </div>

      <form onSubmit={sendMessage} className="p-3 border-t bg-card flex gap-2">
        <Input
          type="text"
          value={inputMessage}
          onChange={(e) => setInputMessage(e.target.value)}
          placeholder={
            chatUser
              ? `Mensagem para ${chatUser.name}...`
              : "Selecione um usuário primeiro"
          }
          disabled={!chatUser || isSending}
          className="flex-1"
        />
        <Button
          type="submit"
          disabled={!chatUser || !inputMessage.trim() || isSending}
        >
          {isSending ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : (
            <Send className="w-4 h-4" />
          )}
        </Button>
      </form>
    </div>
  )
}
