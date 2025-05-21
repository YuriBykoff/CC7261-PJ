#!/bin/bash
set -e # Aborta o script se qualquer comando falhar

# Conecta ao PostgreSQL como superusuário e executa os comandos SQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
    -- Cria o banco de dados para a primeira instância da aplicação
    CREATE DATABASE social_db_1;
    GRANT ALL PRIVILEGES ON DATABASE social_db_1 TO $POSTGRES_USER;

    -- Cria o banco de dados para a segunda instância da aplicação
    CREATE DATABASE social_db_2;
    GRANT ALL PRIVILEGES ON DATABASE social_db_2 TO $POSTGRES_USER;

    -- Cria o banco de dados para a terceira instância da aplicação
    CREATE DATABASE social_db_3;
    GRANT ALL PRIVILEGES ON DATABASE social_db_3 TO $POSTGRES_USER;

    CREATE DATABASE social_db_4;
    GRANT ALL PRIVILEGES ON DATABASE social_db_4 TO $POSTGRES_USER; 
EOSQL

echo "****** DATABASES CREATED ******" 