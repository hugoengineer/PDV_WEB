# Banco de dados e ambiente Docker — PDV Web

## Subir pela primeira vez

Pré-requisito: Docker Desktop (Windows/macOS) ou Docker Engine + Compose (Linux/AWS EC2).

```bash
cd pdv-web-api
bash scripts/criar-env.sh 11144477735   # gera o .env com senhas fortes (informe o CPF do gerente inicial)
docker compose up -d --build            # banco + API + backup
docker compose ps                       # os três serviços devem ficar "healthy"/"running"
```

Na primeira subida:

1. o PostgreSQL 17 cria o banco `pdv_web` com **checksums de página** e senhas **SCRAM-SHA-256**;
2. `postgres/01-papeis.sh` cria os papéis `pdv_migracao` e `pdv_app` ([papeis.sql](postgres/papeis.sql));
3. a API sobe, o Flyway (como `pdv_migracao`) aplica as migrações V1–V4 e cria o gerente inicial;
4. o serviço `backup` faz a primeira cópia em `pdv-web-api/backups/`.

API: `http://localhost:8080` · saúde: `http://localhost:8080/actuator/health`. A porta 8080 só aceita conexões
desta máquina; de fora, o acesso é pelo proxy TLS (seção [HTTPS](#https-proxy-tls)).

## Arquitetura do banco

| Camada | O que garante |
|---|---|
| Restrições (`CHECK`, `UNIQUE`, `FK`) | Estoque nunca negativo, total = subtotal − descontos, um caixa aberto por operador, documento × tipo, cancelamento sempre com motivo/data/gerente, subtotal do item = quantidade × preço |
| Gatilhos (migração V4) | Auditoria, itens de venda e movimentações de caixa são **só inclusão**; venda nunca é apagada nem tem valores alterados (só CONCLUIDA → CANCELADA); caixa fechado não reabre |
| Papéis | A API conecta como `pdv_app`: lê e grava dados, mas **não** cria, apaga, esvazia ou altera tabelas nem desliga gatilhos. Só o Flyway (`pdv_migracao`) muda a estrutura. O superusuário fica para administração e backup |
| Limites de sessão (`pdv_app`) | `statement_timeout` 30 s, `lock_timeout` 10 s, `idle_in_transaction_session_timeout` 60 s, até 60 conexões |
| Servidor | Checksums de dados, log de consultas lentas (> 1 s) e de esperas por bloqueio, fuso America/Sao_Paulo |
| Rede | Porta 5432 publicada só em `127.0.0.1` (ferramentas locais); a API acessa pela rede interna do Compose |

Tudo isso é verificado automaticamente: `BancoDeDadosIT` (CT-DB-002 a 010) e `PapeisDoBancoIT` (CT-DB-011 a 013),
que reproduz estes papéis no PostgreSQL embarcado e sobe a API com `pdv_app`.

## Backup e restauração

| Ação | Comando |
|---|---|
| Backup automático | Serviço `backup`: ao subir e a cada `BACKUP_INTERVALO_HORAS` (24 h), mantém `BACKUP_RETENCAO_DIAS` (7) dias. Cada cópia é conferida com `pg_restore --list` |
| Backup agora | `bash scripts/backup-agora.sh` |
| Restaurar | `bash scripts/restaurar-backup.sh backups/pdv_web_AAAAMMDD_HHMMSS.dump` (para a API, restaura numa única transação e religa a API) |
| Ver os backups | `ls backups/` · log: `docker compose logs backup` |

> Copie a pasta `backups/` para fora do servidor (outro disco, S3) — backup no mesmo disco não protege contra perda da máquina.

## Operação do dia a dia

```bash
docker compose logs -f api                                   # log da API
docker compose exec banco psql -U pdv_admin -d pdv_web       # console SQL (superusuário; pede a DB_ADMIN_PASSWORD do .env)
docker compose restart api                                   # reinicia só a API (sessões continuam válidas)
docker compose down                                          # para tudo; os dados ficam no volume
docker compose down -v                                       # APAGA o banco (volume) — só para recomeçar do zero
```

Conexão por ferramenta gráfica (DBeaver/pgAdmin): host `localhost`, porta `5432`, banco `pdv_web`,
usuário `pdv_admin` (ou `pdv_app` para ver o que a API vê), senha do `.env`.

## HTTPS (proxy TLS)

O serviço `proxy` ([Caddy 2](proxy/Caddyfile)) termina o TLS nas portas 80/443 e repassa para a API. Ele só sobe
com o perfil `tls`, então o uso local continua igual.

| Ambiente | `.env` | Certificado |
|---|---|---|
| Teste nesta máquina | `COMPOSE_PROFILES=tls`, `PDV_DOMINIO=localhost`, `PDV_EXIGIR_HTTPS=true` | CA interna do Caddy (o navegador avisa que não confia, a menos que a CA seja instalada) |
| Produção (AWS EC2) | `COMPOSE_PROFILES=tls`, `PDV_DOMINIO=pdv.suaempresa.com.br`, `PDV_EXIGIR_HTTPS=true` | Let's Encrypt, emitido e renovado sozinho |

Com `PDV_EXIGIR_HTTPS=true`, HTTP recebe 302 para HTTPS e as respostas HTTPS trazem HSTS. O proxy descarta
`X-Forwarded-*` enviados pelo cliente, então ninguém finge estar em HTTPS. Verificado em RT-DEP-05 (Relatório de Execução de Testes, seção 12.3).

Para testar sem mexer no `.env`:

```bash
PDV_EXIGIR_HTTPS=true docker compose --profile tls up -d
docker compose cp proxy:/data/caddy/pki/authorities/local/root.crt ./ca-local.crt
curl -I http://localhost/produtos                                              # 302 -> https://localhost/produtos
curl -I --ssl-no-revoke --cacert ca-local.crt https://localhost/actuator/health  # 200 + Strict-Transport-Security
docker compose --profile tls rm -sf proxy && docker compose up -d              # volta ao modo local
```

No Git Bash, prefixe o `docker compose cp` com `MSYS_NO_PATHCONV=1`. O `--ssl-no-revoke` só é necessário no curl
do Windows, porque a CA local não publica lista de revogação.

### Produção na AWS EC2 (roteiro — ainda não executado, PEND-04/07)

1. Instância Linux (ex.: Ubuntu 24.04, 2 GB de RAM ou mais) com Docker Engine e o plugin Compose.
2. *Security group*: entrada 80 e 443 para todos; 22 só do IP da equipe. **Não** abra 5432 nem 8080.
3. DNS: registro `A` do domínio apontando para o IP elástico da instância (o Let's Encrypt valida pela porta 80).
4. No servidor: `bash scripts/criar-env.sh <CPF>`, depois edite o `.env` com `COMPOSE_PROFILES=tls`,
   `PDV_DOMINIO=<domínio>` e `PDV_EXIGIR_HTTPS=true`; por fim `bash scripts/subir-docker.sh`.
5. Executar RT-DEP-01 a 07 contra o domínio e o teste de carga (VAL-07).
6. Copiar `backups/` para fora da instância (ex.: `aws s3 sync backups/ s3://<bucket>/pdv-web/` agendado).

## Trocar uma senha

As senhas dos papéis são definidas só na criação do volume. Para trocar depois:

```bash
docker compose exec banco psql -U pdv_admin -d pdv_web -c "ALTER ROLE pdv_app PASSWORD 'nova-senha'"
# atualize DB_APP_PASSWORD no .env e:
docker compose up -d api
```
