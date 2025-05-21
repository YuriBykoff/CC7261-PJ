# Estrutura do Projeto

```
.
├── app/
│   ├── api/
│   │   ├── servers/
│   │   └── spring/
│   │       ├── follows/
│   │       │   └── route.ts
│   │       ├── message/
│   │       │   └── route.ts
│   │       ├── notifications/
│   │       │   └── route.ts
│   │       ├── posts/
│   │       │   └── route.ts
│   │       └── users/
│   │           └── route.ts
│   ├── favicon.ico
│   ├── globals.css
│   ├── layout.tsx
│   ├── messages/
│   │   └── page.tsx
│   ├── notifications/
│   │   └── page.tsx
│   ├── page.tsx
│   ├── posts/
│   │   └── page.tsx
│   └── servers/
│       └── page.tsx
├── components.json
├── bun.lock
├── eslint.config.mjs
├── next-env.d.ts
├── next.config.ts
├── package.json
├── postcss.config.mjs
├── README.md
├── server.proto
└── tsconfig.json
```

## Download de Gerenciadores de Pacotes

*   **Bun:** [https://bun.sh/](https://bun.sh/)
*   **npm:** [https://www.npmjs.com/package/download](https://www.npmjs.com/package/download)
*   **pnpm:** [https://pnpm.io/pt/installation](https://pnpm.io/pt/installation)
*   **Yarn:** [https://yarnpkg.com/](https://yarnpkg.com/)

## Como Iniciar

1.  **Instale as dependências:**

    ```bash
    bun install
    # ou
    # npm install
    # ou
    # yarn install
    # ou
    # pnpm install
    ```

2.  **Rode o servidor de desenvolvimento:**
    ```bash
    bun run dev
    # ou
    # npm run dev
    # ou
    # yarn dev
    # ou
    # pnpm dev
    ```

Abra [http://localhost:3000](http://localhost:3000) no seu navegador para ver o resultado.
