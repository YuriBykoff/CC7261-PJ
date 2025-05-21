export interface Message {
  id: string
  senderId: string
  receiverId: string
  content: string
  sentAt: string // Usar string para simplicidade, pode ser Date se preferir tratar
  logicalClock: number
  read: boolean
}
