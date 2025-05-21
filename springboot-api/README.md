# Estrutura do Projeto

```
.
├── container-logs/                 # Logs das aplicações Dockerizadas
│   ├── app1/
│   │   └── app.log
│   ├── app2/
│   │   └── app.log
│   └── app3/
│       └── app.log
├── nginx/                          # Configuração do Nginx (se aplicável)
├── rules/                          # Documentos com regras e especificações do projeto
│   └── projeto.md
├── src/
│   ├── main/
│   │   ├── java/com/example/projeto_sd/
│   │   │   ├── controller/         # Controladores REST API
│   │   │   ├── dto/                # Data Transfer Objects
│   │   │   ├── exception/          # Classes de exceção customizadas
│   │   │   ├── grpc/               # Implementação dos serviços gRPC
│   │   │   ├── model/              # Entidades JPA (Modelos de dados)
│   │   │   ├── repository/         # Repositórios Spring Data JPA
│   │   │   ├── service/            # Lógica de negócios
│   │   │   └── ProjetoSdApplication.java # Classe principal da aplicação Spring Boot
│   │   ├── proto/
│   │   │   └── server.proto        # Definição do serviço gRPC e mensagens (Protocol Buffers)
│   │   └── resources/
│   │       ├── static/             # Arquivos estáticos (CSS, JS, imagens)
│   │       ├── templates/          # Templates (ex: Thymeleaf), se houver
│   │       ├── application.properties # Configurações da aplicação Spring Boot
│   │       └── logback-spring.xml  # Configuração de logging (Logback)
│   └── test/                       # Testes (não detalhado, mas estrutura padrão Java)
├── .gitattributes
├── .gitignore
├── build.gradle                    # Script de build Gradle
├── Dockerfile                      # Dockerfile para construir a imagem da aplicação Java
├── docker-compose.yml              # Orquestração dos contêineres Docker
├── gradlew                         # Wrapper Gradle para Linux/Mac
├── gradlew.bat                     # Wrapper Gradle para Windows
├── README.md                       # Este arquivo
└── settings.gradle                 # Configurações do projeto Gradle
```

## Como Usar

Siga os passos abaixo para configurar e executar o projeto em seu ambiente local.

### 1. Pré-requisitos

Certifique-se de que você tem os seguintes softwares instalados em sua máquina:

*   **Java Development Kit (JDK):** Versão 17 ou superior (conforme especificado no `build.gradle` e `Dockerfile`).
*   **Docker:** Para executar a aplicação em contêineres.
*   **Docker Compose:** Para orquestrar os múltiplos contêineres da aplicação (aplicação, banco de dados, Consul, etc.).

### 2. Clonar o Repositório

Clone este repositório para sua máquina local usando o seguinte comando:

```bash
https://github.com/YuriBykoff/PJ-CC7261
cd springboot-api 
```

### 3. Construir a Aplicação Java

Navegue até a raiz do projeto clonado (a pasta `springboot-api`) e execute o seguinte comando para construir a aplicação Java usando o Gradle Wrapper. Isso irá gerar o arquivo `.jar` executável.

No Linux ou macOS:
```bash
./gradlew clean build -x test
```

No Windows:
```bash
gradlew.bat clean build -x test
```

**Importante**: Para evitar problemas de compatibilidade, verifique se o arquivo 'docker/db/init/init-db.sh' está no modo LF (Line Feed) em vez de CRLF (Carriage Return Line Feed). Você pode verificar e alterar isso facilmente abrindo o arquivo com um editor de texto como o VSCode e modificando a opção no canto inferior direito da tela. Caso contrário, o Linux apresentará problemas ao gerar o banco de dados.

### 4. Construir Imagens Docker e Iniciar os Contêineres

Ainda na raiz do projeto, onde o arquivo `docker-compose.yml` está localizado, execute o seguinte comando para construir as imagens Docker (se ainda não tiverem sido construídas) e iniciar todos os serviços definidos no `docker-compose.yml` (aplicações, bancos de dados, Consul, Nginx):

```bash
docker-compose up --build -d
```

*   `--build`: Força a reconstrução das imagens Docker. Útil se você fez alterações no `Dockerfile` ou no código-fonte.
*   `-d`: Executa os contêineres em modo "detached" (em segundo plano).

Aguarde alguns instantes para que todos os contêineres sejam iniciados e as aplicações estejam prontas. Você pode verificar o status dos contêineres com `docker-compose ps` e os logs com `docker-compose logs -f [nome_do_servico]`.

```bash
docker-compose logs postgres_db
docker-compose logs nginx_lb

docker logs social_app_1
docker logs social_app_2
docker logs social_app_3
```


### 5. Acessando a Aplicação

Após os contêineres estarem em execução:

*   **API REST:** As instâncias da aplicação geralmente são expostas através de um gateway (como o Nginx configurado na porta 80) ou diretamente em suas portas. Verifique o `docker-compose.yml` para as portas mapeadas. Por exemplo, se o Nginx estiver configurado para a porta `80` no host, você pode acessar os endpoints da API através de `http://localhost/api/...`.
*   **Serviços gRPC:** Os serviços gRPC estarão disponíveis nas portas gRPC de cada instância da aplicação (ex: `9090`, `9091`, `9092`, conforme definido e mapeado no `docker-compose.yml`).
*   **Consul UI:** Sua UI está acessível em `http://localhost:8500`.

### 6. Parando a Aplicação

Para parar todos os contêineres da aplicação, execute o seguinte comando na raiz do projeto:

```bash
docker-compose down
```

Se você também quiser remover os volumes (o que apagará os dados persistidos pelos bancos de dados, por exemplo), use:
```bash
docker-compose down -v
```

## Acesso

*   **API REST (via Load Balancer):** `http://localhost/api/...`
*   **Servidores gRPC:**
    *   `app1`: `localhost:9090`
    *   `app2`: `localhost:9091`
    *   `app3`: `localhost:9092`
*   **Logs dos Servidores (Host):** `./container-logs/<app-name>/app.log`


