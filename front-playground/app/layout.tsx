import type { Metadata } from "next"
import { Inter } from "next/font/google"
import "./globals.css"
import { Toaster } from "@/components/ui/sonner"
import { Sidebar } from "@/components/sidebar"
import { UserProvider } from "@/context/user-context"
import { ThemeProvider } from "@/components/theme-provider"

const inter = Inter({ subsets: ["latin"] })

export const metadata: Metadata = {
  title: "Playground App",
  description: "App para testes de componentes",
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="pt-br" suppressHydrationWarning>
      <body className={inter.className}>
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          disableTransitionOnChange
        >
          <div
            className="fixed inset-0 z-[-1] opacity-20 pointer-events-none"
            style={{
              backgroundImage: 'url("/noise.png")',
              backgroundRepeat: "repeat",
              backgroundSize: "auto",
            }}
          />
          <UserProvider>
            <div className="relative z-10 flex h-screen overflow-hidden bg-transparent">
              <Sidebar />
              <main className="flex-1 overflow-y-auto overflow-x-hidden pl-64">
                <div className="p-6">{children}</div>
              </main>
            </div>
            <Toaster richColors />
          </UserProvider>
        </ThemeProvider>
      </body>
    </html>
  )
}
