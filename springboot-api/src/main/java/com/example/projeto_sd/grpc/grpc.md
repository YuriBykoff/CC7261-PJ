# Documentação do Serviço gRPC (ServerServiceImpl)

## Métodos RPC Principais

### registerOrUpdateRemoteServer
- **Request:** `ServerInfo`
- **Response:** `ServerRegistrationResponse`
- Registra ou atualiza dados de um servidor remoto no banco local.

### getServerList
- **Request:** `Empty`
- **Response:** `ServerListResponse`
- Retorna lista de servidores ativos conhecidos.

### getTime
- **Request:** `GetTimeRequest`
- **Response:** `GetTimeResponse`
- Fornece o tempo atual do servidor, ajustado pelo offset de sincronização.

### adjustServerTime
- **Request:** `AdjustTimeRequest`
- **Response:** `AdjustTimeResponse`
- Aplica ajuste de relógio recebido via `ClockSyncService`.

### receiveHeartbeat
- **Request:** `HeartbeatRequest`
- **Response:** `HeartbeatResponse`
- Processa heartbeat de outro nó, delegando a `HeartbeatService`.

### announceCoordinator
- **Request:** `CoordinatorAnnouncement`
- **Response:** `Empty`
- Recebe anúncio de novo coordenador, via `ElectionService`.

### createUserRPC
- **Request:** `CreateUserRequest`
- **Response:** `UserResponse`
- Cria novo usuário (apenas no coordenador), inicia replicação em seguidores.

### replicateNotification
- **Request:** `ReplicateNotificationRequest`
- **Response:** `ReplicationResponse`
- Replica notificação gerada localmente para outros nós.

### forwardMarkNotificationsRead
- **Request:** `MarkNotificationsReadRequest`
- **Response:** `Empty`
- Encaminha comando de marcar notificações como lidas ao coordenador.

## Métodos RPC de Replicação e Ações de Leitura/Escrita

### replicateUserCreation
- **Request:** `UserInfo`
- **Response:** `ReplicationResponse`
- Replica criação de usuário no nó local.

### followUserRPC
- **Request:** `FollowRequest`
- **Response:** `ReplicationResponse`
- Solicitação para seguir usuário (no coordenador), inicia replicação.

### unfollowUserRPC
- **Request:** `FollowRequest`
- **Response:** `ReplicationResponse`
- Solicitação para deixar de seguir usuário (no coordenador), inicia replicação.

### replicateFollow
- **Request:** `FollowRequest`
- **Response:** `ReplicationResponse`
- Replica ação de seguir localmente.

### replicateUnfollow
- **Request:** `FollowRequest`
- **Response:** `ReplicationResponse`
- Replica ação de deixar de seguir localmente.

### createPostRPC
- **Request:** `CreatePostRequest`
- **Response:** `CreatePostResponse`
- Cria nova postagem (no coordenador), inicia replicação.

### replicatePostCreation
- **Request:** `PostInfo`
- **Response:** `ReplicationResponse`
- Replica criação de postagem localmente.

### deletePostRPC
- **Request:** `DeletePostRequest`
- **Response:** `ReplicationResponse`
- Solicitação para deletar postagem (no coordenador), inicia replicação.

### replicatePostDeletion
- **Request:** `ReplicatePostDeletionRequest`
- **Response:** `ReplicationResponse`
- Replica deleção de postagem localmente.

### replicateMarkNotificationsRead
- **Request:** `MarkNotificationsReadRequest`
- **Response:** `ReplicationResponse`
- Replica marcação de notificações como lidas.

### sendMessageRPC
- **Request:** `SendMessageRequest`
- **Response:** `SendMessageResponse`
- Envia mensagem (no coordenador), inicia replicação.

### replicateMessage
- **Request:** `ReplicateMessageRequest`
- **Response:** `ReplicationResponse`
- Replica mensagem criada localmente.

## Fluxo de Encaminhamento e Replicação

- O método `handleSimpleCoordinatorForwarding` centraliza lógica de leitura do nó atual:
  - Se for coordenador: executa ação local e retorna sucesso.
  - Caso contrário: localiza coordenador e encadeia a chamada via `GrpcClientService`.

## Métodos Auxiliares de Conversão

- `toUserInfoProto(User)` / `toUserResponseProto(User)`:
  Convertem `User` do modelo para mensagens protobuf.
- `toPostInfoProto(Post)`:
  Converte `Post` em `PostInfo`, incluindo validações de integridade.
- `toMessageInfoProto(MessageDTO)`:
  Converte `MessageDTO` em `MessageInfo`, garantindo campos não-nulos.

## Tratamento de Erros gRPC

- `handleGrpcError`, `handleCoordinatorNotFoundError`, `handleForwardingError`:
  Capturam exceções, fazem log e retornam status gRPC apropriado (e.g. `UNAVAILABLE`, `INTERNAL`).
- `mapExceptionToStatus(Exception e)`: (Usado internamente por `handleGrpcError`)
  Mapeia exceções Java comuns para códigos de status gRPC.

## Métodos Auxiliares Internos

### Registro de Servidor
- `buildRegistrationResponse(boolean success, String message)`:
  Constrói a resposta padrão para o registro de servidor.
- `processServerRegistration(ServerInfo request)`:
  Orquestra o processo de registro/atualização de servidor.
- `updateExistingServer(Server server, ServerInfo request)`:
  Atualiza os dados de um servidor já existente.
- `createNewServer(ServerInfo request)`:
  Cria um novo registro de servidor.

### Replicação
- `replicateUserCreationToFollowers(User user)`:
  Inicia a replicação da criação de um usuário para todos os seguidores.
- `replicateToServer(Server server, UserInfo userInfo)`:
  Envia a requisição gRPC de replicação de usuário para um nó específico.
- `replicateFollowToFollowers(String followerId, String followedId)`:
  Inicia a replicação da ação de "seguir" para todos os seguidores.
- `replicateUnfollowToFollowers(String followerId, String followedId)`:
  Inicia a replicação da ação de "deixar de seguir" para todos os seguidores.
- `validateReplicationRequest(UserInfo request)`:
  Valida os dados recebidos em uma requisição de replicação de usuário.
- `processUserReplication(UserInfo request)`:
  Processa e salva um usuário recebido via replicação.
- `handleReplicationFailure(String serverId, String operation)`:
  Loga falha na replicação (quando não há exceção, ex: resposta gRPC com `success=false`).
- `handleReplicationException(String serverId, String operation, Exception e)`:
  Loga erro durante a replicação (quando ocorre uma exceção na chamada gRPC).

---
Esta documentação resume as responsabilidades de cada RPC e auxiliares em `ServerServiceImpl`. Para detalhes de implementação, consulte diretamente o código fonte.