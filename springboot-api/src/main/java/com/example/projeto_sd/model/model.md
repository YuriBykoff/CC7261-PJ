# Documentação das Entidades do Modelo

## User
- `id` (String): Chave primária, não nulo.
- `name` (String): Nome do usuário, não nulo.

## ServerClock
- `id` (String): Chave primária.
- `server` (Server): Relação OneToOne para o servidor.
- `offsetMillis` (int): Diferença em milissegundos para tempo de referência.
- `lastUpdated` (LocalDateTime): Data/hora da última atualização.

## Server
- `id` (String): Chave primária.
- `serverName` (String): Nome descritivo do servidor.
- `host` (String): Hostname ou IP.
- `port` (Integer): Porta gRPC.
- `isActive` (boolean): Indica se está ativo.
- `isCoordinator` (boolean): Indica se é coordenador.
- `createdAt` (LocalDateTime): Timestamp de criação.
- `updatedAt` (LocalDateTime): Timestamp da última atualização.
- `lastHeartbeatReceived` (LocalDateTime): Último heartbeat recebido.

## Notification
- `id` (String): Chave primária.
- `user` (User): ManyToOne para o usuário destinatário.
- `type` (String): Tipo da notificação.
- `message` (String): Texto da notificação.
- `relatedEntityId` (String): ID da entidade relacionada.
- `read` (boolean): Status de leitura.
- `createdAt` (LocalDateTime): Timestamp de criação.

## Post
- `id` (String): Chave primária.
- `user` (User): ManyToOne para o autor.
- `content` (String): Conteúdo do post.
- `createdAt` (LocalDateTime): Timestamp de criação.
- `logicalClock` (int): Relógio lógico para ordenação causal.
- `isDeleted` (boolean): Indica deleção lógica.
- `server` (Server): ManyToOne para servidor de origem.

## Message
- `id` (String): Chave primária.
- `sender` (User): ManyToOne para remetente.
- `receiver` (User): ManyToOne para destinatário.
- `content` (String): Conteúdo da mensagem.
- `sentAt` (LocalDateTime): Timestamp de envio.
- `logicalClock` (int): Relógio lógico.
- `isRead` (boolean): Indica se foi lida.
- `server` (Server): ManyToOne para servidor de processamento.

## Follow
- `id` (String): Chave primária.
- `follower` (User): ManyToOne para usuário que segue.
- `followed` (User): ManyToOne para usuário seguido.
- `createdAt` (LocalDateTime): Timestamp de criação.