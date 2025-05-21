# Rede Social Distribuída com gRPC

## Visão Geral

Este projeto implementa um sistema distribuído para uma rede social, permitindo interações como criação de usuários, seguir outros usuários e, futuramente, postar textos e trocar mensagens privadas. O sistema é projetado para alta disponibilidade e consistência de dados através de múltiplos servidores replicados. Utiliza algoritmos de eleição de coordenador (Bullying), sincronização de relógios (Berkeley - conceitual) e replicação de dados para manter a integridade do sistema. A comunicação entre os componentes é realizada predominantemente via gRPC, com uma API REST disponível para interações cliente-servidor tradicionais.

O sistema foi desenvolvido para atender aos requisitos especificados no arquivo `rules/projeto.md`.

## Arquitetura

*   **Servidores de Aplicação:** 3 instâncias de uma aplicação Spring Boot (Java), cada uma com sua própria base de dados PostgreSQL. Essas instâncias se comunicam via gRPC para eleição de coordenador, replicação de dados e sincronização.
*   **Banco de Dados:** PostgreSQL (uma instância por servidor de aplicação para simular distribuição e replicação).
*   **Load Balancer:** Nginx para distribuir tráfego REST para os servidores de aplicação.
*   **Descoberta de Serviços (Opcional/Anteriormente):** Consul foi utilizado para descoberta de serviços, mas a configuração atual pode variar (ex: IPs estáticos para eleição).
*   **Comunicação Inter-Servidor:** gRPC para chamadas de procedimento remoto entre as instâncias da aplicação.
*   **Comunicação Cliente-Servidor:** API REST exposta via Nginx e serviços gRPC diretamente acessíveis.
*   **Frontend:** Um serviço Next.js pode ser usado como cliente para consumir a API REST dos servidores Java.
*   **Containerização:** Docker e Docker Compose para gerenciar e orquestrar todos os serviços.

## Como Usar e Documentação Específica

Para informações detalhadas sobre cada componente principal do sistema, consulte seus respectivos READMEs:

*   **Backend API (Spring Boot/Java):** [Documentação Detalhada](https://github.com/YuriBykoff/PJ-CC7261/tree/main/springboot-api#readme)
*   **Frontend (Next.js Playground):** [Documentação do Frontend](https://github.com/YuriBykoff/PJ-CC7261/tree/main/front-playground#readme)
*   **Cliente Python (Exemplo/Testes):** [Documentação do Cliente Python](https://github.com/YuriBykoff/PJ-CC7261/blob/main/python-client/README.md) (Nota: o código principal deste cliente pode ter sido removido ou alterado, verifique o repositório para o estado atual).
