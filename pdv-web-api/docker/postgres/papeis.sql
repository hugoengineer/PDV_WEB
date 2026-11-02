-- =====================================================================
-- Papéis (usuários) do banco pdv_web — princípio do menor privilégio
--   pdv_migracao : dono do esquema; usado só pelo Flyway para criar/alterar tabelas
--   pdv_app      : usado pela API; lê e grava dados, mas não cria, altera nem apaga tabelas
-- O superusuário (POSTGRES_USER) fica reservado para administração e backup.
--
-- Executado uma única vez, na criação do volume, por 01-papeis.sh (psql -v ...).
-- Também é executado pelo teste PapeisDoBancoIT, que confere as permissões.
-- =====================================================================

CREATE ROLE pdv_migracao LOGIN PASSWORD :'senha_migracao';
CREATE ROLE pdv_app LOGIN PASSWORD :'senha_app' CONNECTION LIMIT 60;

-- Limites da sessão da API: nenhuma consulta ou transação esquecida trava o banco
ALTER ROLE pdv_app SET statement_timeout = '30s';
ALTER ROLE pdv_app SET lock_timeout = '10s';
ALTER ROLE pdv_app SET idle_in_transaction_session_timeout = '60s';
ALTER ROLE pdv_app SET timezone = 'America/Sao_Paulo';
ALTER ROLE pdv_migracao SET lock_timeout = '30s';

-- Ninguém além dos papéis do sistema conecta ou cria objetos
REVOKE ALL ON DATABASE pdv_web FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE pdv_web TO pdv_migracao, pdv_app;
ALTER DATABASE pdv_web OWNER TO pdv_migracao;

REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO pdv_migracao;
GRANT USAGE ON SCHEMA public TO pdv_app;

-- Tudo o que o Flyway criar já nasce com as permissões da API
ALTER DEFAULT PRIVILEGES FOR ROLE pdv_migracao IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO pdv_app;
ALTER DEFAULT PRIVILEGES FOR ROLE pdv_migracao IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO pdv_app;
ALTER DEFAULT PRIVILEGES FOR ROLE pdv_migracao IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO pdv_app;
