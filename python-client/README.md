# Estrutura do Projeto

```
.
├── .gitignore
├── client.py
├── README.md
├── requirements.txt
├── server.proto
├── server_pb2.py
├── server_pb2_grpc.py
└── venv/
```

# Como Usar

Este projeto consiste em um cliente e um servidor que se comunicam usando gRPC.

## Pré-requisitos

- Python 3.x
- pip (gerenciador de pacotes Python)

## Instalação

1.  **Clone o repositório (se aplicável):**
    ```bash
    https://github.com/YuriBykoff/PJ-CC7261
    cd python-client
    ```

2.  **Crie e ative um ambiente virtual (recomendado):**
    ```bash
    python -m venv venv
    # No Windows
    .\venv\Scripts\activate
    # No macOS/Linux
    source venv/bin/activate
    ```

3.  **Instale as dependências:**
    ```bash
    pip install -r requirements.txt
    ```

4.  **Compile os arquivos .proto (se ainda não estiverem compilados):**
    Os arquivos `server_pb2.py` e `server_pb2_grpc.py` são gerados a partir de `server.proto`. Se você precisar recompilá-los (por exemplo, após modificar `server.proto`), use o seguinte comando:
    ```bash
    python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. server.proto
    ```

## Executando


1.  **Execute o cliente (`client.py`):**
    Este arquivo deve ser executado **após** o servidor da API estar completamente iniciado e pronto para receber conexões. Recomenda-se aguardar pelo menos 30 segundos após iniciar o servidor antes de executar o cliente para garantir que o servidor esteja operacional.
    ```bash
    python client.py localhost:9093
    ```

## Arquivos Principais

-   `client.py`: Contém a lógica do cliente gRPC.
-   `server.proto`: Define a estrutura dos serviços e mensagens gRPC.
-   `server_pb2.py`: Contém as classes de mensagem Python geradas a partir de `server.proto`.
-   `server_pb2_grpc.py`: Contém os stubs e servicer do gRPC Python gerados a partir de `server.proto`.
-   `requirements.txt`: Lista as dependências Python do projeto.

