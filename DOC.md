# Documentação do Projeto de Sistemas Distribuídos

## 1. Visão Geral da Arquitetura

Este projeto é uma plataforma social onde as pessoas podem postar conteúdo, seguir outros usuários e trocar mensagens. Ele foi construído usando vários servidores (nós) que trabalham juntos. Cada servidor guarda uma cópia dos dados e eles se coordenam para que tudo funcione de forma consistente e para que o sistema continue no ar mesmo que algum servidor falhe. Para organizar os eventos e manter o tempo sincronizado entre os servidores, usamos duas técnicas: o algoritmo de Berkeley para os relógios físicos e os relógios de Lamport para os lógicos.

*   **Componentes principais:**
    *   **Servidores da Aplicação (Backend em Spring Boot/Java):** Temos várias cópias do nosso programa principal rodando (chamadas `app1`, `app2`, `app3`, por exemplo). Eles oferecem uma API REST para que os clientes (como a interface web) possam interagir e também usam gRPC para conversar entre si sobre coisas internas, como decidir quem é o líder, copiar dados e sincronizar os relógios. Qualquer um desses servidores pode ser um nó comum ou o coordenador do grupo.
    *   **Banco de Dados (PostgreSQL):** Cada servidor da aplicação tem seu próprio banco de dados PostgreSQL onde guarda as informações (usuários, posts, mensagens, etc.). A aplicação se encarrega de copiar esses dados entre os bancos, com o servidor coordenador organizando essa tarefa.
    *   **Consul:** O Consul desempenha um papel crucial, permitindo que os servidores se descubram dinamicamente (service discovery) e verificando sua saúde (health checking). O `ElectionService`, por exemplo, utiliza o Consul (através do `DiscoveryClient` do Spring Cloud) para encontrar todos os outros servidores da aplicação ativos durante o processo de eleição de coordenador. Ele atua como um catálogo central e atualizado dos serviços disponíveis na rede.
    *   **Nginx (Load Balancer/Gateway de API):** O Nginx é como um porteiro principal para os pedidos que chegam pela API REST dos clientes. Ele distribui esses pedidos entre os servidores da aplicação que estão disponíveis, ajudando a não sobrecarregar nenhum deles.
    *   **Interface Web (Frontend em Next.js):** É a página web que as pessoas usam. Ela conversa com a API REST do backend (através do Nginx) para mostrar as informações e permitir que os usuários usem as funcionalidades da plataforma.
    *   **Cliente Python (Ferramenta de Teste/Interação Direta):** Um script em Python que se comunica diretamente com os serviços gRPC do backend. Ele é usado principalmente para realizar testes automatizados das funcionalidades ou para interações diretas com os servidores, sem passar pela API REST ou Nginx.

*   (Opcional, mas recomendado) Insira um diagrama simples da arquitetura aqui.

## 2. Funcionamento dos Serviços Principais

A interação com os serviços principais da plataforma é feita através de uma API REST. Abaixo, detalhamos as funcionalidades de cada serviço, referenciando os endpoints principais.

### 2.1. Serviço de Usuários
Este serviço gerencia as informações e interações dos usuários.

*   **Criação de Usuário:**
    *   **Endpoint:** `POST /api/users`
    *   **Descrição:** Permite registrar um novo usuário na plataforma.
    *   **Fluxo da requisição:** O `UserController` recebe a requisição, chama o `UserService` que valida os dados e, se tudo estiver correto, cria uma nova entrada de usuário no banco de dados através do `UserRepository`. Essa criação é então replicada para os demais nós pelo coordenador.
    *   **Dados armazenados/replicados:** Informações do usuário (ID, nome, etc.).
*   **Listar Todos os Usuários:**
    *   **Endpoint:** `GET /api/users`
    *   **Descrição:** Retorna uma lista de todos os usuários cadastrados. A requisição pode ser atendida por qualquer nó.
*   **Listar Seguidores de um Usuário:**
    *   **Endpoint:** `GET /api/users/{userId}/followers`
    *   **Descrição:** Retorna a lista de usuários que seguem o `userId` especificado.
*   **Listar Usuários que um Usuário Segue (Following):**
    *   **Endpoint:** `GET /api/users/{userId}/following`
    *   **Descrição:** Retorna a lista de usuários que o `userId` especificado está seguindo.

### 2.2. Serviço de Postagem e Seguir
Este serviço cuida da criação e visualização de postagens, bem como do sistema de seguir usuários.

*   **Criação de Post:**
    *   **Endpoint:** `POST /api/posts`
    *   **Descrição:** Permite que um usuário autenticado crie uma nova postagem.
    *   **Fluxo da requisição:** O `PostController` recebe o conteúdo do post e o ID do usuário. O `PostService` processa a criação, associa um timestamp lógico (Relógio de Lamport) ao post para ordenação causal e o persiste no banco de dados através do `PostRepository`. O coordenador então replica este novo post para os outros nós.
    *   **Dados armazenados/replicados:** Conteúdo do post, ID do autor, timestamp de criação, timestamp lógico.
    *   **Relógio lógico:** Um timestamp lógico é atribuído no momento da criação para ajudar na ordenação de eventos distribuídos.
*   **Listar Todas as Postagens:**
    *   **Endpoint:** `GET /api/posts`
    *   **Descrição:** Retorna uma lista de todas as postagens. Pode ser usado para um feed global ou para propósitos de administração/teste. A requisição pode ser atendida por qualquer nó.
*   **Listar Postagens de um Usuário Específico:**
    *   **Endpoint:** `GET /api/posts/user/{userId}`
    *   **Descrição:** Retorna todas as postagens feitas pelo `userId` especificado.
*   **Excluir uma Postagem:**
    *   **Endpoint:** `DELETE /api/posts/{postId}`
    *   **Descrição:** Permite que o autor de uma postagem (ou um administrador) a exclua. A exclusão também é uma operação replicada.
*   **Seguir um Usuário:**
    *   **Endpoint:** `POST /api/follows/{followerId}/follow/{followedId}`
    *   **Descrição:** Permite que o usuário `followerId` comece a seguir o usuário `followedId`.
    *   **Fluxo da requisição:** O `FollowController` processa a requisição, e o `FollowService` atualiza as relações de "seguir" no banco de dados. Essa informação é replicada.
    *   **Dados armazenados/replicados:** Relações de quem segue quem.
*   **Deixar de Seguir um Usuário:**
    *   **Endpoint:** `DELETE /api/follows/{followerId}/unfollow/{followedId}`
    *   **Descrição:** Permite que o usuário `followerId` deixe de seguir o usuário `followedId`. A alteração é replicada.

### 2.3. Serviço de Troca de Mensagens
Responsável pela comunicação privada entre usuários.

*   **Envio de Mensagem Direta:**
    *   **Endpoint:** `POST /api/messages`
    *   **Descrição:** Permite que um usuário envie uma mensagem privada para outro usuário.
    *   **Fluxo da requisição:** O `MessageController` recebe a mensagem (remetente, destinatário, conteúdo). O `MessageService` atribui um timestamp lógico à mensagem, a persiste no banco de dados via `MessageRepository` e o coordenador se encarrega de replicá-la.
    *   **Dados armazenados/replicados:** Conteúdo da mensagem, remetente, destinatário, timestamp de envio, timestamp lógico.
    *   **Relógio lógico:** Um timestamp lógico é atribuído para ajudar na ordenação das mensagens dentro de uma conversa e entre eventos distribuídos.
*   **Obter Conversa entre Dois Usuários:**
    *   **Endpoint:** `GET /api/users/{userId1}/conversation/{userId2}`
    *   **Descrição:** Retorna o histórico de mensagens trocadas entre `userId1` e `userId2`, ordenadas (possivelmente pelo timestamp lógico e/ou de criação).

### 2.4. Serviço de Notificações
Este serviço gerencia as notificações para os usuários sobre eventos relevantes.

*   **Obter Notificações Não Lidas de um Usuário:**
    *   **Endpoint:** `GET /api/users/{userId}/notifications`
    *   **Descrição:** Retorna uma lista de notificações não lidas para o `userId` especificado. Notificações podem ser geradas por eventos como novos seguidores, mensagens, etc.
    *   **Fluxo da requisição:** O `NotificationController` busca, através do `NotificationService` e `NotificationRepository`, as notificações pendentes para o usuário. A leitura das notificações não costuma envolver replicação crítica imediata, mas o estado de "lida" pode ser replicado.
    *   **Dados armazenados/replicados:** Conteúdo da notificação, tipo, destinatário, status (lida/não lida), timestamp.
*   **Marcar Notificações como Lidas:**
    *   **Endpoint:** `POST /api/users/{userId}/notifications/mark-read`
    *   **Descrição:** Marca todas ou algumas notificações do `userId` como lidas.
    *   **Fluxo da requisição:** O `NotificationController` processa a requisição. O `NotificationService` atualiza o status das notificações correspondentes no banco de dados. Essa mudança de estado (para "lida") é replicada para garantir consistência entre os nós.
    *   **Dados armazenados/replicados:** Atualização do status das notificações.

_(Nota: O endpoint de Teste (`/api/test`) existe conforme `controller.md`. Se for relevante para a avaliação, pode ser detalhado adicionalmente.)_

## 3. Mecanismos de Sistemas Distribuídos Implementados

Para garantir o funcionamento correto e a robustez da plataforma em um ambiente distribuído, diversos mecanismos foram implementados:

### 3.1. Eleição de Coordenador

Quando os servidores da nossa aplicação começam a rodar, ou se o líder atual some, eles precisam decidir quem vai ser o "chefe" (o coordenador). Esse chefe tem tarefas importantes, como organizar a cópia de dados entre os servidores e dar o pontapé inicial na sincronização dos relógios.

*   **Quem faz isso?** O trabalho de eleição fica por conta do `ElectionService.java` em cada servidor.

*   **Como a eleição começa?**
    *   Basicamente, se um servidor percebe que não tem um coordenador definido, ele mesmo inicia o processo para tentar virar o líder ou para descobrir quem é.

*   **Qual é a lógica da disputa?**
    1.  **Identificando os vizinhos:** Primeiro, o servidor consulta os mapas estáticos de peers (`PEER_HOSTS` e `PEER_INTERNAL_GRPC_PORTS` definidos no `ApplicationInitializer.java`) para obter a lista de todos os servidores da aplicação conhecidos que participam do cluster. O `DiscoveryClient` (Consul) pode ser utilizado para obter detalhes adicionais desses peers (como endereços IP dinâmicos, se aplicável) ou para obter informações sobre um coordenador recém-anunciado por outro nó.
    2.  **Quem é o "maior"?** Cada servidor tem um nome (ID) único. O servidor que está tentando a eleição compara o seu próprio ID com o ID dos outros servidores que encontrou. A ideia é ver se existe algum outro servidor ativo com um ID "maior" que o dele (a comparação é feita como se fosse em ordem alfabética).
    3.  **Decidindo ser o líder:** Se o servidor olha em volta e não acha ninguém ativo com ID maior que o seu, ele pensa: "Ok, parece que sou o melhor candidato aqui!" e decide se tornar o coordenador.
    4.  **Esperando os outros:** Se ele encontra outros servidores com ID maior, ele fica na dele e espera que um desses servidores "maiores" se anuncie como o coordenador. (Ele não manda uma mensagem "ei, você quer ser o líder?", mas espera que o líder natural apareça).

*   **O que acontece quando um servidor vira o coordenador?**
    1.  **Se auto-promove:** Ele primeiro atualiza o seu próprio banco de dados, marcando a si mesmo como o coordenador e garantindo que os outros servidores (no seu banco local) não estejam marcados como coordenadores.
    2.  **Avisa todo mundo:** Depois, ele usa gRPC para mandar uma mensagem para todos os outros servidores ativos (identificados a partir dos mapas estáticos em `ApplicationInitializer.java`), dizendo: "E aí, pessoal, eu sou o novo coordenador!"

*   **E quando um servidor recebe o aviso de um novo coordenador?**
    1.  **Anota quem é o chefe:** Ele guarda a informação de qual servidor é o novo líder.
    2.  **Se não for ele, abaixa a crista:** Se por acaso esse servidor achava que *ele* era o coordenador, ele corrige isso e para de se considerar o líder (inclusive atualizando seu banco de dados).
    3.  **Atualiza o status local:** Ele também atualiza seu banco de dados para marcar corretamente quem é o novo coordenador e garantir que outros não estejam indevidamente marcados como tal.
    4.  Se uma eleição estava acontecendo nesse servidor, ela é interrompida, já que um líder foi definido.

### 3.2. Replicação de Dados

Manter os dados iguais em todos os servidores é super importante. Usamos uma tática onde o "chefe" (o coordenador) é o principal responsável por qualquer mudança nos dados.

*   **Quais dados são copiados?** Basicamente, tudo que importa: informações dos usuários, os posts, as mensagens diretas, quem segue quem, e o status das notificações (se foram lidas, por exemplo).

*   **Como funciona quando algo novo é criado ou alterado (ex: um novo post)?**

    1.  **Chegou um pedido:** Um cliente (como a interface web) manda um pedido para criar um novo post. Esse pedido chega em um dos nossos servidores (normalmente através do Nginx, que distribui a carga).

    2.  **Quem manda aqui?** O servidor que recebeu o pedido (vamos chamá-lo de Nó A) verifica com o `ElectionService` se ele é o coordenador.

    3.  **"Não sou eu, manda pro chefe!" (Se o Nó A não é o coordenador):**
        *   O Nó A descobre quem é o coordenador (consultando o `ElectionService`).
        *   Ele então usa o `GrpcClientService` para "repassar" o pedido original de criar o post para o servidor coordenador. Essa comunicação é feita via gRPC (especificamente, o Nó A chama um método como `forwardCreatePostRPC` no `GrpcClientService`, que por sua vez chama o método `createPostRPC` no `ServerServiceImpl` do coordenador).

    4.  **"Deixa comigo!" (Se o Nó A é o coordenador, ou se o pedido foi encaminhado para ele):**
        *   **Salva localmente:** O coordenador (digamos, no `PostService` através do método `processAndReplicatePost`) primeiro salva o novo post no seu próprio banco de dados PostgreSQL.
        *   **Avisa os outros para copiarem:** Depois de salvar, o coordenador pega os dados desse novo post e, usando o `GrpcClientService` (método `replicatePostCreationToPeer`), manda uma mensagem gRPC para cada um dos outros servidores ativos (as réplicas). Essa mensagem diz basicamente: "Ei, servidor B, salva aí esse post novo!".
        *   A chamada para as réplicas (`replicatePostCreationToPeer`) é feita para o método `replicatePostCreation` no `ServerServiceImpl` de cada réplica.

    5.  **Réplicas entram em ação:**
        *   Cada servidor réplica, ao receber a mensagem do coordenador via `ServerServiceImpl` (no método `replicatePostCreation`), pega os dados do post e salva no seu próprio banco de dados local (usando o `PostService` local, método `saveReplicatedPost`).
        *   A réplica também atualiza seu relógio lógico com base no valor recebido do coordenador (`logicalClock.synchronizeWith(logicalClockDoCoordenador)`).

*   **E a resposta para o cliente?**
    *   **Se o pedido foi encaminhado:** O nó original que encaminhou o pedido espera a resposta do coordenador e então responde ao cliente.
    *   **Se foi o coordenador que processou diretamente:** O coordenador, após salvar localmente e *iniciar* o processo de enviar os dados para as réplicas, já responde ao cliente que o post foi criado. Ele não espera que todas as réplicas confirmem que salvaram antes de responder. Isso torna a operação mais rápida para o usuário.
    *   A replicação para os outros nós acontece em "segundo plano" (de forma assíncrona do ponto de vista do cliente inicial).

*   **Como fica a consistência?**
    *   A ideia é que, depois que o coordenador confirma uma escrita, os dados vão se espalhar para os outros servidores. Isso é chamado de "consistência eventual". Pode levar um tempinho muito curto para que todos os servidores tenham exatamente a mesma visão dos dados, mas eles chegam lá.

Este padrão de "verificar se é coordenador -> se sim, processa e replica / se não, encaminha para o coordenador -> coordenador processa, salva e manda replicar -> réplicas salvam" é seguido para outras operações de escrita como criar mensagens (`MessageService`), criar usuários (`UserService`), seguir/deixar de seguir (`FollowService` em conjunto com o `ServerServiceImpl` para orquestrar) e marcar notificações como lidas.

### 3.3. Sincronização de Relógios (Algoritmo de Berkeley)
*   **Serviço responsável:** `ClockSyncService.java`.
*   **Objetivo:** Minimizar as diferenças entre os relógios físicos dos diversos nós do sistema, o que é importante para a consistência de timestamps e para o funcionamento de alguns algoritmos distribuídos.
*   **Papel do coordenador:** O coordenador da aplicação também atua como "mestre de tempo" (Time Master) no algoritmo de Berkeley.
*   **Fluxo do algoritmo:**
    1.  Periodicamente, o coordenador (mestre de tempo) solicita o tempo atual de todos os outros nós (escravos) via gRPC.
    2.  Os nós escravos respondem com seus horários locais.
    3.  O coordenador calcula a diferença (offset) entre seu próprio relógio e o de cada escravo, e então calcula uma média desses offsets (desconsiderando valores muito discrepantes, se implementado).
    4.  O coordenador envia a cada escravo o ajuste de tempo necessário (o valor do offset calculado para ele ou um ajuste global baseado na média).
    5.  Os nós escravos aplicam esse ajuste aos seus relógios locais. O `ClockSyncService` também armazena esse offset localmente para referência e para simular a aplicação contínua do ajuste.
*   **Simulação de Drift:** O serviço inclui uma funcionalidade para simular um "drift" (desvio) aleatório nos relógios dos nós, tornando o processo de sincronização mais realista e testável.
*   **Persistência de Offset:** Os offsets calculados e aplicados são persistidos, permitindo que o nó mantenha uma noção do seu desvio em relação ao tempo de referência mesmo entre reinicializações (se essa persistência for entre sessões) ou durante a execução.

### 3.4. Sincronização de Relógios (Lógicos - Lamport)

Além de tentar manter os relógios dos computadores em sincronia (com o Algoritmo de Berkeley), precisamos de uma forma de saber a "ordem" em que as coisas acontecem dentro do nosso sistema distribuído. Por exemplo, se você envia uma mensagem e depois faz um post, gostaríamos que essa ordem fosse respeitada. Para isso, usamos os Relógios Lógicos de Lamport.

*   **Para que serve?** Eles nos dão uma maneira de colocar os eventos importantes (como criar um post ou enviar uma mensagem) em uma ordem que faça sentido, mesmo que os relógios físicos dos servidores não estejam 100% iguais.

*   **Como funciona?**
    *   **Cada servidor tem um contador:** Usamos uma classe chamada `LogicalClock.java`. Cada servidor tem o seu próprio "contador lógico" (um número inteiro que só aumenta).
    *   **Evento local? Aumenta o contador!** Toda vez que algo importante acontece no servidor (um "evento local"), como ele decidir criar um novo post ou uma nova mensagem, ele primeiro incrementa seu contador lógico. O método `increment()` no `LogicalClock.java` faz isso.
        *   Por exemplo, no `MessageService.java`, antes de o coordenador salvar uma nova mensagem (no método `processAndReplicateMessage`), ele chama `logicalClock.increment()`.

    *   **Mandando o contador junto:** Quando um servidor envia dados para outro (por exemplo, quando o coordenador replica uma mensagem para os outros servidores), ele inclui o valor atual do seu contador lógico junto com esses dados.
        *   No `MessageService.java`, quando o coordenador prepara a mensagem para replicação, o valor do `logicalClock.getValue()` é incluído nos dados da mensagem (no `MessageInfo` do Protobuf).

    *   **Recebeu dados? Sincroniza o contador!** Quando um servidor (uma réplica) recebe dados de outro servidor (do coordenador) que contêm um contador lógico (`valorRecebido`):
        1.  Ele pega o seu próprio contador local (`valorLocal`).
        2.  Ele calcula: `novoValor = maiorNumeroEntre(valorLocal, valorRecebido) + 1`.
        3.  Ele atualiza o seu contador local para esse `novoValor`.
        *   Isso é feito pelo método `synchronizeWith(int receivedClock)` na classe `LogicalClock.java`.
        *   Por exemplo, no `MessageService.java`, quando uma réplica está salvando uma mensagem que veio do coordenador (método `saveReplicatedMessage`), ela primeiro chama `logicalClock.synchronizeWith(messageInfo.getLogicalClock())`.

*   **Qual a utilidade disso na prática?**
    *   Quando criamos posts ou mensagens, o valor do relógio lógico daquele momento é salvo junto com eles.
    *   Isso nos ajuda a ordenar as mensagens dentro de uma conversa ou os posts em um feed de uma maneira que respeite a relação de "aconteceu antes de". Se a mensagem A foi criada com relógio 5 e a mensagem B (que pode ser uma resposta a A ou relacionada) foi criada depois com relógio 8, sabemos a ordem delas mesmo que tenham sido processadas por servidores diferentes com relógios físicos levemente dessincronizados.

*   **Persistência de Offset:** Os offsets calculados e aplicados são persistidos, permitindo que o nó mantenha uma noção do seu desvio em relação ao tempo de referência mesmo entre reinicializações (se essa persistência for entre sessões) ou durante a execução.

