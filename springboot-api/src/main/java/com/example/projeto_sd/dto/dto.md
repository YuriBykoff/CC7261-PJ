# Documentação dos DTOs

## CreateUserRequestDTO
DTO para criação de usuário.
- Campo `name` (String): Nome do usuário. Não pode ser vazio (@NotBlank).

## UserResponseDTO
DTO de resposta de usuário.
- Campo `id` (String): UUID gerado.
- Campo `name` (String): Nome do usuário.

## ErrorResponse
DTO de erro.
- Campo `message` (String): Mensagem de erro.

## ApiResponse
DTO genérico de resposta.
- Campo `message` (String): Mensagem de sucesso ou informação.

## CreatePostRequestDto
DTO para criação de postagem.
- Campo `userId` (String): ID do usuário que cria a postagem.
- Campo `content` (String): Conteúdo da postagem.

## DeletePostRequestDto
DTO para requisição de exclusão de postagem.
- Campo `userId` (String): ID do usuário solicitando exclusão.

## PostResponseDto
DTO de resposta de postagem.
- Campo `id` (String): ID da postagem.
- Campo `userId` (String): ID do autor.
- Campo `userName` (String): Nome do autor.
- Campo `content` (String): Conteúdo da postagem.
- Campo `createdAt` (LocalDateTime): Data e hora de criação.
- Campo `logicalClock` (int): Relógio lógico associado.

## NotificationDTO
DTO de notificação.
- Campo `id` (String): ID da notificação.
- Campo `type` (String): Tipo de notificação.
- Campo `message` (String): Texto da notificação.
- Campo `relatedEntityId` (String): ID da entidade relacionada.
- Campo `createdAt` (LocalDateTime): Data e hora.
- Campo `read` (boolean): Status de leitura.

## MessageDTO
DTO de mensagem.
- Campo `id` (String): ID da mensagem.
- Campo `senderId` (String): ID do remetente.
- Campo `receiverId` (String): ID do destinatário.
- Campo `content` (String): Conteúdo da mensagem.
- Campo `sentAt` (LocalDateTime): Data e hora do envio.
- Campo `logicalClock` (int): Relógio lógico.
- Campo `isRead` (boolean): Indica se foi lida.

## CreateMessageRequestDTO
DTO para criação de mensagem.
- Campo `senderId` (String): ID do remetente. Não pode ser nulo ou vazio.
- Campo `receiverId` (String): ID do destinatário. Não pode ser nulo ou vazio.
- Campo `content` (String): Conteúdo. Não pode ser nulo ou vazio.