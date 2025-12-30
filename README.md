# JocoTerrenos

Sistema de gerenciamento de terrenos para servidores Minecraft (Spigot/Paper). Permite comprar, listar, visualizar informações, configurar opções (PvP/Mobs/Acesso público) e teleportar para locais seguros. Durante o desenvolvimento existem utilitários internos para cercas, mas eles não fazem parte do uso público.

## Principais recursos
- Compra de terrenos quadrados via comando, com custo calculado pelo tamanho
- Listagem e visualização de informações do terreno atual ou por nome
- Configurações por terreno: PvP, Mobs e Acesso Público
- Teleporte para um local seguro dentro do terreno
- Persistência em banco de dados com HikariCP (SQLite/MySQL/PostgreSQL)

## Compatibilidade
- Servidores: Spigot/Paper (versão da API conforme pom.xml do projeto)
- Java: versão usada pelo seu servidor (Java 17+ recomendado)

## Instalação (produção)
1. Copie o arquivo `.jar` gerado para `plugins/` do seu servidor.
2. Inicie o servidor para gerar `config.yml` e arquivos necessários.
3. Ajuste `plugins/JocoTerrenos/config.yml` conforme seu banco e preferências.
4. Reinicie o servidor.

## Configuração do banco de dados
O plugin suporta três tipos de banco (definido em `resources/config.yml`):

SQLite (padrão)
```yaml
database:
  type: SQLITE
  sqlite:
    filename: terrenos.db
  pool:
    maximum-pool-size: 10
    minimum-idle: 2
    maximum-lifetime: 1800000
    connection-timeout: 5000
    idle-timeout: 600000
```

MySQL
```yaml
database:
  type: MYSQL
  mysql:
    host: localhost
    port: 3306
    database: jocoterrenos
    username: root
    password: senha
    properties:
      useSSL: false
      autoReconnect: true
  pool:
    maximum-pool-size: 10
    minimum-idle: 2
    maximum-lifetime: 1800000
    connection-timeout: 5000
    idle-timeout: 600000
```

PostgreSQL
```yaml
database:
  type: POSTGRESQL
  postgresql:
    host: localhost
    port: 5432
    database: jocoterrenos
    username: postgres
    password: senha
    schema: public
  pool:
    maximum-pool-size: 10
    minimum-idle: 2
    maximum-lifetime: 1800000
    connection-timeout: 5000
    idle-timeout: 600000
```

## Comandos
Terrenos (`/terreno`):
- `/terreno comprar [tamanho] [nome]` — Compra um terreno NxN com o nome indicado
  - Ex.: `/terreno comprar 10 casa`
- `/terreno preco [tamanho]` — Mostra o preço para um terreno NxN
- `/terreno listar` — Lista seus terrenos
- `/terreno info [nome]` — Mostra info do terreno atual (sem nome) ou de um nome específico
- `/terreno deletar [nome]` — Deleta o terreno indicado (remove cercas associadas)
- `/terreno pvp [nome]` — Alterna PvP do terreno
- `/terreno mobs [nome]` — Alterna mobs do terreno
- `/terreno publico [nome]` — Alterna acesso público do terreno
- `/terreno tp [nome]` — Teleporta você para um local seguro dentro do terreno

<!-- Comandos de cerca são internos ao desenvolvimento e permanecem ocultos no README -->

## Permissões
As permissões dos comandos de terreno podem ser configuradas conforme necessidade do servidor. Atualmente, o controle principal é por propriedade do terreno.

## Estrutura do banco de dados
Tabela: `terrenos`
- `id` — ID do terreno
- `dono_uuid` — UUID do dono
- `location` — Localização central do terreno (string serializada)
- `size` — Tamanho (N => NxN)
- `pvp` — Booleano
- `mobs` — Booleano
- `public_access` — Booleano
- `created_at`, `updated_at` — timestamps

Tabela: `terreno_members`
- `id` — ID do registro
- `terreno_id` — FK para `terrenos`
- `member_uuid` — UUID do membro
- `member_role` — Papel (OWNER, ADMIN, MEMBER, VISITOR)
- `added_at` — timestamp

## Como construir a partir do código-fonte
Pré-requisitos: JDK instalado e Maven.

- Compilar e empacotar:
```powershell
mvn -v ; mvn clean package
```
O jar será gerado em `target/jocoterrenos-1.0-SNAPSHOT.jar`.

## Desenvolvimento
Estrutura principal do código (`src/main/java/io/github/joaovmundel/jocoTerrenos`):
- `commands/` — Comandos de jogo (Terreno, utilitários internos de cercas durante desenvolvimento)
- `service/` — Regras de negócio (ex.: `TerrenoService`)
- `repositories/` — Acesso ao banco (ex.: `TerrenoRepository`)
- `database/` — Configuração e gerência (`DatabaseManager`)
- `models/` — Entidades (`Terreno`, `TerrenoMember`, `TerrenoRole`)
- `utils/` — Utilitários (localização, cercas, strings)
- `infrastructure/` — Logging e afins

Arquivos de recursos:
- `plugin.yml` — Metadados do plugin
- `config.yml` — Configurações (banco, etc.)

### Dicas
- Métodos que interagem com o mundo (teleporte, cercas) devem executar no main thread do Bukkit.
- Operações pesadas de banco podem ser tratadas de forma assíncrona, retornando ao main thread apenas para interações com o Bukkit API.
- Valide entradas de comando e trate exceções (ex.: `TerrenoNotFoundException`).

## Troubleshooting
- Erro de conexão ao banco: verifique credenciais/host/porta; para SQLite, confirme permissões de escrita no diretório do servidor.
- Comandos não reconhecidos: confira `plugin.yml` e se o jar correto está em `plugins/`.
- Teleporte inseguro: use `/terreno tp [nome]` para garantir tentativa de posição segura; ajuste lógica em `SafeLocationUtils` conforme seu servidor.

## Roadmap
- Proteção física das áreas (flag de interação/rompimento)
- Permissões por terreno (membros/roles mais granulares)
- GUI para gerenciamento
- Integração com economia (Vault)
- Limites por jogador e taxa/aluguel
- Visualização de bordas
- Convites e fluxo de aprovação