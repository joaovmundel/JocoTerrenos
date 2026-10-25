# JocoTerrenos (port para Spigot 1.5.2)

Sistema de terrenos pagos para o servidor **CraftBukkit/Spigot 1.5.2** (`git-Spigot-934`, Java 8). O jogador fica no
local, compra um terreno quadrado com `/terreno comprar`, o plugin cobra pelo Vault, cerca o perímetro e protege a
área. Esta branch (`port/1.5.2`) é o port da versão para Spigot 1.21 que vive na `main`.

## Principais recursos
- Compra de terrenos quadrados via comando, com custo `tamanho² × block-price` (Vault)
- Listagem e visualização de informações do terreno atual ou por nome
- Configurações por terreno: PvP, Mobs e entrada de visitantes
- Teleporte para um local seguro dentro do terreno
- Proteção dos terrenos (construir, quebrar, abrir baús, balde, fogo, explosões, líquidos, pistões, PvP e mobs)
- Terrenos privados: só o dono e os membros entram; quem ficou preso dentro é levado para a borda de fora
- Venda de terrenos entre jogadores (`/terreno vender`, `/terreno adquirir`)
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
| Textos | UTF-8 com acentos | **ASCII puro**, sem acentos (a 1.5.2 não lida bem com eles) |

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

Ao atualizar o plugin, `locales/*.yml` e o banco se atualizam sozinhos: um locale mais antigo que o do jar é guardado
como `pt_BR.yml.v1.bak` e trocado pelo novo, e colunas novas do banco são criadas sem perder terrenos.

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
- `/terreno publico <nome>` — Libera ou fecha a entrada de visitantes no terreno
- `/terreno tp <nome>` — Teleporta você para um local seguro dentro do terreno
- `/terreno vender <nome> <valor>` — Coloca o terreno à venda (o valor é a última palavra; vender de novo muda o preço)
- `/terreno cancelarvenda <nome>` — Tira o terreno da venda
- `/terreno vendas` — Lista os terrenos à venda, do mais barato para o mais caro
- `/terreno adquirir [dono] [nome]` — Compra um terreno à venda: sem argumentos, o terreno em que você está

Nomes de terreno podem ter mais de uma palavra (até 15 caracteres).

## Proteção
Quem não é dono nem membro do terreno não consegue construir, quebrar, abrir baús, usar baldes, acender fogo nem
mexer em animais, molduras e quadros dentro dele. Explosões, fogo, líquidos vindos de fora e pistões que atravessam a
borda também não afetam o terreno.

- **PvP**: desligado, ninguém causa dano em jogadores quando atacante ou vítima está no terreno.
- **Mobs**: desligado, monstros não nascem no terreno (ovos de spawn e comandos continuam funcionando).
- **Entrada** (`/terreno publico`): todo terreno novo nasce **público**, e qualquer um pode entrar. O dono o deixa
  **privado** com `/terreno publico <nome>` (de novo para reabrir). Num terreno privado só entram o dono e os membros:
  quem tenta andar, navegar ou se teleportar para dentro é barrado; um teleporte para dentro (`/home`, `/back`, ender
  pearl) vira o ponto mais próximo logo fora da borda. Quem já está dentro quando o terreno fecha, ou que marcou uma
  home antes dele ficar privado, é levado para fora em vez de ficar preso.
- Público libera **só a entrada**. Construir, quebrar, abrir baús e usar portas, alçapões, portões, botões, alavancas e
  placas de pressão continua só para o dono e os membros, em qualquer terreno.
- **Terreno à venda** pode ser visitado por qualquer um (para quem quer comprar conhecer), sem poder construir.
- Quem tem `jocoterrenos.bypass` (OP por padrão) ignora tudo isso: para testar a proteção, use um jogador sem OP.
- **Membros** (papéis `OWNER`, `ADMIN`, `MEMBER`) constroem como o dono. Ainda não há comando para adicionar membros.
- A área protegida é o anel das cercas: para tamanhos pares ela tem uma linha de blocos a mais que o tamanho comprado.
- Limites conhecidos: dispensers, funis, poções arremessadas e crescimento de árvores não são tratados.

## Venda de terrenos
- O preço é um valor positivo com até 2 casas decimais, no máximo `lands.sale.max-price` (padrão 1000000000).
- Na compra, o dinheiro vai do comprador para o dono (mesmo offline). O terreno muda de dono, deixa de estar à venda,
  volta ao padrão de um terreno novo (público) e perde os membros do antigo dono. Se o comprador já tem um terreno com o mesmo nome, o novo
  ganha um número (`Casa` vira `Casa2`).
- Se algum passo falha, os anteriores são desfeitos (o dinheiro volta). Falhas que não puderem ser desfeitas vão para o
  log como `SEVERE` com os valores.
- Preços aparecem no formato da economia do servidor (Vault).

## Permissões
| Permissão | Padrão | Efeito |
|---|---|---|
| `jocoterrenos.terreno` | todos | Usar `/terreno` (comprar e gerenciar os próprios terrenos) |
| `jocoterrenos.bypass` | OP | Ignorar a proteção de qualquer terreno |
| `jocoterrenos.cercar`, `jocoterrenos.resizecerca`, `jocoterrenos.removercerca` | OP | Comandos de teste de cercas, que mexem no mundo em qualquer lugar |
| `jocoterrenos.admin` | OP | Todas as permissões administrativas acima |

## Estrutura do banco de dados
Tabela `terrenos`: `id`, `dono_nome` (minúsculas), `name`, `db_name_key` (dono+nome, único), `location`
(`mundo:x:y:z`, centro do bloco), `size`, `pvp`, `mobs`, `public_access`, `created_at`, `updated_at` (milissegundos), `sale_price_cents` (centavos inteiros; vazio quando não está à venda).

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
- Mensagens com textos antigos ou cores erradas: o plugin só troca `locales/*.yml` quando o `locale-version` do jar é
  maior; se você editou o arquivo e atualizou o plugin, a sua versão fica em `*.bak` na mesma pasta.
- Linhas extras no console: deixe `debug: false` no `config.yml` (é o padrão).

## Roadmap
- Comandos de membros (adicionar, remover, papéis)
- Placa clicável no terreno à venda
- Proteção contra dispensers, funis, poções e crescimento de árvores
- Barrar a entrada de quem está montado em porco (só barcos e carrinhos são tratados)
- Limites por jogador, confirmação de compra e reembolso ao deletar
