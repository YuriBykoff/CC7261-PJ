# Documentação dos Endpoints do Controller

## UserController (/api/users)
- `POST /api/users`: Cria um novo usuário.
- `GET /api/users`: Lista todos os usuários.
- `GET /api/users/{userId}/followers`: Lista seguidores de um usuário.
- `GET /api/users/{userId}/following`: Lista usuários que o usuário segue.

## PostController (/api/posts)
- `POST /api/posts`: Cria uma nova postagem.
- `GET /api/posts`: Lista todas as postagens.
- `GET /api/posts/user/{userId}`: Lista postagens de um usuário.
- `DELETE /api/posts/{postId}`: Exclui uma postagem.

## FollowController (/api/follows)
- `POST /api/follows/{followerId}/follow/{followedId}`: Segue um usuário.
- `DELETE /api/follows/{followerId}/unfollow/{followedId}`: Deixa de seguir um usuário.

## MessageController (/api)
- `POST /api/messages`: Envia uma mensagem.
- `GET /api/users/{userId1}/conversation/{userId2}`: Obtém a conversa entre dois usuários.

## NotificationController (/api)
- `GET /api/users/{userId}/notifications`: Obtém notificações não lidas de um usuário.
- `POST /api/users/{userId}/notifications/mark-read`: Marca notificações como lidas.

## TestController (/api/test)
- `GET /api/test`: Endpoint de teste.