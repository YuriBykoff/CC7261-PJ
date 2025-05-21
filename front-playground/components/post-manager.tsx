"use client"

import { useState, useEffect, FormEvent } from "react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Textarea } from "@/components/ui/textarea"
import { Loader2, Send, Trash2, MessageSquare, Users } from "lucide-react"
import { toast } from "sonner"
import { useUser } from "@/context/user-context"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"

interface Post {
  id: string
  content: string
  userId: string
  createdAt: string
  userName: string 
  deleted: boolean
  logicalClock: number 
  serverId: string 
}

interface CreatePostData {
  userId: string
  content: string
}

export function PostManager() {
  const [userPosts, setUserPosts] = useState<Post[]>([])
  const [allPosts, setAllPosts] = useState<Post[]>([])
  const [newPostContent, setNewPostContent] = useState("")
  const [loadingUserPosts, setLoadingUserPosts] = useState(false)
  const [loadingAllPosts, setLoadingAllPosts] = useState(false)
  const [loadingCreate, setLoadingCreate] = useState(false)
  const [loadingDelete, setLoadingDelete] = useState<string | null>(null)
  const { selectedUser } = useUser()
  const currentUserId = selectedUser?.id || null

  const fetchUserPosts = async () => {
    if (!currentUserId) return

    setLoadingUserPosts(true)
    try {
      const response = await fetch(
        `/api/spring/posts/get-user-post?userId=${currentUserId}`
      )
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(
          errorData.error || "Falha ao carregar posts do usuário (API Next)"
        )
      }
      const data = await response.json()
      const posts: Post[] = data.content || data || [] // Lida com resposta paginada ou direta
      setUserPosts(
        posts.sort(
          (a, b) =>
            new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        )
      )
    } catch (error) {
      const errorMsg =
        error instanceof Error
          ? error.message
          : "Erro ao carregar posts do usuário"
      toast.error(errorMsg)
      console.error("Erro ao carregar posts do usuário:", error)
      setUserPosts([])
    } finally {
      setLoadingUserPosts(false)
    }
  }

  const fetchAllPosts = async () => {
    setLoadingAllPosts(true)
    try {
      const response = await fetch("/api/spring/posts/get-all-posts")
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(
          errorData.error || "Falha ao carregar feed geral (API Next)"
        )
      }
      const data = await response.json()
      const posts: Post[] = data.content || data || [] // Lida com resposta paginada ou direta
      setAllPosts(
        posts.sort(
          (a, b) =>
            new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        )
      )
    } catch (error) {
      const errorMsg =
        error instanceof Error ? error.message : "Erro ao carregar feed geral"
      toast.error(errorMsg)
      console.error("Erro ao carregar feed geral:", error)
      setAllPosts([])
    } finally {
      setLoadingAllPosts(false)
    }
  }

  const handleCreatePost = async (event: FormEvent) => {
    event.preventDefault()
    if (!currentUserId || !newPostContent.trim()) return

    setLoadingCreate(true)
    try {
      const response = await fetch("/api/spring/posts/create-post", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          userId: currentUserId,
          content: newPostContent.trim(),
        } as CreatePostData),
      })

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.error || "Falha ao criar post (API Next)")
      }
  
      toast.success("Post criado com sucesso!")
      setNewPostContent("")
      fetchUserPosts()

    } catch (error) {
      const errorMsg =
        error instanceof Error
          ? error.message
          : "Erro desconhecido ao criar post"
      toast.error(`Erro ao criar post: ${errorMsg}`)
      console.error("Erro ao criar post:", error)
    } finally {
      setLoadingCreate(false)
    }
  }

  // Deletar post
  const handleDeletePost = async (postId: string) => {
    if (!currentUserId) return

    setLoadingDelete(postId)
    try {
      const response = await fetch(
        `/api/spring/posts/delete-post?postId=${postId}`,
        {
          method: "DELETE",
          headers: { "Content-Type": "application/json" }, 
          body: JSON.stringify({ userId: currentUserId }),
        }
      )

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.error || "Falha ao deletar post (API Next)")
      }
      toast.success("Post deletado com sucesso")
      fetchUserPosts() 

    } catch (error) {
      const errorMsg =
        error instanceof Error
          ? error.message
          : "Erro desconhecido ao deletar post"
      toast.error(`Erro ao deletar post: ${errorMsg}`)
      console.error("Erro ao deletar post:", error)
    } finally {
      setLoadingDelete(null)
    }
  }

  useEffect(() => {
    fetchAllPosts() 
    if (!currentUserId) {
      setUserPosts([])
    } else {
      fetchUserPosts()
    }
  }, [currentUserId])

  if (!currentUserId) {
    return (
      <div className="flex items-center justify-center h-[600px] text-muted-foreground">
        Selecione um usuário para ver/criar posts
      </div>
    )
  }

  const PostItem = ({ post }: { post: Post }) => (
    <div
      key={post.id}
      className="group rounded-lg border bg-background/30 p-4 space-y-3 hover:bg-muted/5 transition-colors"
    >
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Users className="h-4 w-4" />
        <span>{post.userName || "Usuário desconhecido"}</span>
      </div>
      <p className="text-sm leading-relaxed">{post.content}</p>
      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>
          {post.createdAt
            ? new Date(post.createdAt).toLocaleString("pt-BR")
            : "Data indisponível"}
        </span>
        {post.userId === currentUserId && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleDeletePost(post.id)}
            disabled={loadingDelete === post.id}
            className="opacity-0 group-hover:opacity-100 transition-opacity text-destructive hover:text-destructive hover:bg-destructive/10"
          >
            {loadingDelete === post.id ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Trash2 className="h-4 w-4" />
            )}
          </Button>
        )}
      </div>
    </div>
  )

  return (
    <div className="space-y-12">
      <form onSubmit={handleCreatePost} className="space-y-4">
        <div className="space-y-2">
          <div className="flex items-center gap-2 px-1">
            <MessageSquare className="h-5 w-5 text-primary" />
            <label className="text-base font-medium">Criar Novo Post</label>
          </div>
          <Textarea
            placeholder="O que você está pensando?"
            value={newPostContent}
            onChange={(e) => setNewPostContent(e.target.value)}
            rows={4}
            disabled={loadingCreate}
            required
            className="resize-none bg-muted/5 border-dashed focus:border-primary"
          />
        </div>
        <Button
          type="submit"
          className="w-full bg-primary/10 hover:bg-primary/20 text-primary hover:text-primary"
          disabled={loadingCreate || !newPostContent.trim()}
        >
          {loadingCreate ? (
            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          ) : (
            <Send className="mr-2 h-5 w-5" />
          )}
          Publicar Post
        </Button>
      </form>

      <Tabs defaultValue="user" className="space-y-4">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="user">Meus Posts</TabsTrigger>
          <TabsTrigger value="all">Feed Geral</TabsTrigger>
        </TabsList>

        <TabsContent value="user" className="space-y-4">
          <div className="flex items-center gap-2 px-1">
            <MessageSquare className="h-5 w-5 text-primary" />
            <label className="text-base font-medium">
              Meus Posts ({userPosts.length})
            </label>
          </div>

          {loadingUserPosts ? (
            <div className="flex justify-center items-center h-[500px] text-muted-foreground">
              <Loader2 className="h-8 w-8 animate-spin" />
            </div>
          ) : (
            <ScrollArea className="h-[500px] rounded-lg border bg-muted/5">
              <div className="p-6 space-y-6">
                {userPosts.length === 0 ? (
                  <div className="flex flex-col items-center justify-center h-[400px] text-muted-foreground">
                    <MessageSquare className="h-12 w-12 mb-4 opacity-20" />
                    <p>Você ainda não tem posts.</p>
                  </div>
                ) : (
                  userPosts.map((post) => (
                    <PostItem key={post.id} post={post} />
                  ))
                )}
              </div>
            </ScrollArea>
          )}
        </TabsContent>

        <TabsContent value="all" className="space-y-4">
          <div className="flex items-center gap-2 px-1">
            <Users className="h-5 w-5 text-primary" />
            <label className="text-base font-medium">
              Feed Geral ({allPosts.length})
            </label>
          </div>

          {loadingAllPosts ? (
            <div className="flex justify-center items-center h-[500px] text-muted-foreground">
              <Loader2 className="h-8 w-8 animate-spin" />
            </div>
          ) : (
            <ScrollArea className="h-[500px] rounded-lg border bg-muted/5">
              <div className="p-6 space-y-6">
                {allPosts.length === 0 ? (
                  <div className="flex flex-col items-center justify-center h-[400px] text-muted-foreground">
                    <MessageSquare className="h-12 w-12 mb-4 opacity-20" />
                    <p>Nenhum post encontrado.</p>
                  </div>
                ) : (
                  allPosts.map((post) => <PostItem key={post.id} post={post} />)
                )}
              </div>
            </ScrollArea>
          )}
        </TabsContent>
      </Tabs>
    </div>
  )
}
