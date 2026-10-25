# JocoTerrenos (port para Spigot 1.5.2)

Sistema de terrenos pagos para o servidor **CraftBukkit/Spigot 1.5.2** (`git-Spigot-934`, Java 8). O jogador fica no
local, compra um terreno quadrado com `/terreno comprar`, o plugin cobra pelo Vault, cerca o perímetro e protege a
área. Esta branch (`port/1.5.2`) é o port da versão para Spigot 1.21 que vive na `main`.

## Principais recursos
- Compra de terrenos quadrados via comando, com custo `tamanho² × block-price` (Vault)
- Listagem e visualização de informações do terreno atual ou por nome
- Configurações por terreno: PvP, Mobs e Acesso Público
- Teleporte para um local seguro dentro do terreno
- Proteção dos terrenos (construir, quebrar, abrir baús, balde, fogo, explosões, líquidos, pistões, PvP e mobs)
- Persistência em SQLite (padrão) ou MySQL, com os drivers que o próprio servidor já traz

## Diferenças em relação à `main` (1.21)
| Assunto | `main` (1.21) | `port/1.5.2` |
|---|---|---|
| Identidade do jogador | UUID | **nome em minúsculas** (a 1.5.2 não tem UUID) |
| Banco | SQLite, MySQL, PostgreSQL com HikariCP | SQLite e MySQL, uma conexão simples (como o Solary-Economy) |
| Economia | Vault, buscada ao ligar o plugin | Vault, buscada a cada compra (`EconomyGateway`) |
| `/terreno listar` | nomes clicáveis (JSON) | texto simples |
| Assíncrono | `CompletableFuture` | tudo na thread principal (consultas vêm da memória) |
| Linguagem | Java 17+ | código Java 7, bytecode Java 8 |

Como o servidor é *offline-mode*, nomes são falsificáveis: a proteção só vale enquanto o AuthMe impedir que alguém
entre com o nome de outro jogador.

## Compatibilidade
- Servidor: CraftBukkit/Spigot 1.5.2 (API `1.5.2-R0.1`), Java 8
- Obrigatório: **Vault** e um plugin de economia (ex.: Solary-Economy)

## Instalação
1. Compile (veja abaixo) e copie `build/jocoterrenos-1.0-SNAPSHOT.jar` para `plugins/` com o servidor **parado**.
2. Inicie o servidor: o plugin cria `plugins/JocoTerrenos/config.yml`, `locales/` e `terrenos.db`.
3. Confira o log por `JocoTerrenos habilitado com sucesso!` e por `SEVERE`/`WARNING`.
4. Conceda as permissões pelo PermissionsEx (veja a tabela abaixo).

## Configuração do banco de dados
SQLite (padrão)
```yaml
database:
  type: SQLITE
  sqlite:
    filename: terrenos.db
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
```
PostgreSQL não é suportado nesta versão (o servidor não traz o driver). Se o banco não puder ser aberto ou lido, o
plugin se desabilita em vez de rodar sem proteção.

## Comandos
Terrenos (`/terreno`):
- `/terreno comprar [tamanho] [nome]` — Compra um terreno NxN com o nome indicado (ex.: `/terreno comprar 10 casa`)
- `/terreno preco [tamanho]` — Mostra o preço para um terreno NxN
- `/terreno listar` — Lista seus terrenos
- `/terreno info [nome]` — Mostra info do terreno atual (sem nome) ou de um nome específico
- `/terreno deletar [nome]` — Deleta o terreno indicado (remove cercas associadas, sem reembolso)
- `/terreno pvp [nome]` — Alterna PvP do terreno
- `/terreno mobs [nome]` — Alterna mobs do terreno
- `/terreno publico [nome]` — Alterna acesso público do terreno
- `/terreno tp [nome]` — Teleporta você para um local seguro dentro do terreno

Nomes de terreno podem ter mais de uma palavra (até 15 caracteres).

## Proteção
Quem não é dono nem membro do terreno não consegue construir, quebrar, abrir baús, usar baldes, acender fogo nem
mexer em animais, molduras e quadros dentro dele. Explosões, fogo, líquidos vindos de fora e pistões que atravessam a
borda também não afetam o terreno.

- **PvP**: desligado, ninguém causa dano em jogadores quando atacante ou vítima está no terreno.
- **Mobs**: desligado, monstros não nascem no terreno (ovos de spawn e comandos continuam funcionando).
- **Público**: visitantes podem usar portas, alçapões, portões, botões, alavancas e placas de pressão, mas não
  construir nem abrir baús.
- **Membros** (papéis `OWNER`, `ADMIN`, `MEMBER`) constroem como o dono. Ainda não há comando para adicionar membros.
- A área protegida é o anel das cercas: para tamanhos pares ela tem uma linha de blocos a mais que o tamanho comprado.
- Limites conhecidos: dispensers, funis, poções arremessadas e crescimento de árvores não são tratados.

## Permissões
| Permissão | Padrão | Efeito |
|---|---|---|
| `jocoterrenos.terreno` | todos | Usar `/terreno` (comprar e gerenciar os próprios terrenos) |
| `jocoterrenos.bypass` | OP | Ignorar a proteção de qualquer terreno |
| `jocoterrenos.cercar`, `jocoterrenos.resizecerca`, `jocoterrenos.removercerca` | OP | Comandos de teste de cercas, que mexem no mundo em qualquer lugar |
| `jocoterrenos.admin` | OP | Todas as permissões administrativas acima |

## Estrutura do banco de dados
Tabela `terrenos`: `id`, `dono_nome` (minúsculas), `name`, `db_name_key` (dono+nome, único), `location`
(`mundo:x:y:z`, centro do bloco), `size`, `pvp`, `mobs`, `public_access`, `created_at`, `updated_at` (milissegundos).

Tabela `terreno_members`: `id`, `terreno_id` (FK com `ON DELETE CASCADE`), `member_nome`, `member_role`
(`OWNER`, `ADMIN`, `MEMBER`), `added_at`. Não há migração a partir do banco da `main` (que usa UUID).

## Como construir
Pré-requisitos: JDK 9+ (compila com `--release 8`) e Maven. Não precisa do servidor.
```powershell
mvn          # clean package: compila, roda os testes e gera build\jocoterrenos-1.0-SNAPSHOT.jar
```
Os testes rodam sem servidor, inclusive o repositório contra o SQLite 3.7.2 (o driver embutido no servidor) e a
leitura de `plugin.yml`/locales com as classes reais da API 1.5.2.

## Desenvolvimento
Estrutura (`src/main/java/io/github/joaovmundel/jocoTerrenos`):
- `commands/` — comandos; só validam entrada e chamam o serviço
- `service/` — regras de negócio (`TerrenoService`), mensagens e consulta de proteção
- `protection/` — núcleo puro da proteção (sem Bukkit): `Bounds`, `Claim`, `ClaimIndex`, regras
- `listener/` — eventos do Bukkit traduzidos em perguntas ao `ProtectionService`
- `repositories/`, `database/` — acesso ao banco (uma conexão compartilhada, só na thread principal)
- `economy/` — Vault atrás de `EconomyGateway`
- `models/`, `utils/`, `infrastructure/`

Regras do código: Java 7 (sem lambdas, `Optional`, streams), API 1.5.2 verificada com `javap` antes de usar.

## Troubleshooting
- Plugin se desabilitou no início: leia o `SEVERE` no `server.log` (banco inacessível ou tabelas ilegíveis).
- Compra recusada com "falha": confira saldo, nome único, tamanho e se há economia registrada no Vault.
- Acentos errados nas mensagens: os arquivos em `locales/` precisam estar em UTF-8.

## Roadmap
- Comandos de membros (adicionar, remover, papéis) e transferência de terreno
- Proteção contra dispensers, funis, poções e crescimento de árvores
- Limites por jogador, confirmação de compra e reembolso ao deletar
