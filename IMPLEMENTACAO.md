# Sistema de Repository com Banco de Dados - Resumo da Implementação

## 📦 O que foi criado

### 1. **DatabaseManager** (`database/DatabaseManager.java`)
Gerenciador central de conexões com banco de dados que suporta:
- ✅ **SQLite** (padrão) - Banco local, sem configuração
- ✅ **MySQL** - Para servidores maiores
- ✅ **PostgreSQL** - Alta performance

**Características:**
- Pool de conexões com HikariCP
- Criação automática de tabelas
- Configuração via `config.yml`
- Suporte a múltiplos bancos de dados
- Tratamento de erros robusto

### 2. **TerrenoRepository** (`repositories/TerrenoRepository.java`)
Repository completo com todas as operações CRUD:

**Operações de Terreno:**
- `create(Terreno)` - Cria um novo terreno
- `findById(Long)` - Busca por ID
- `findByDonoUUID(String)` - Busca terrenos de um jogador
- `findAll()` - Lista todos os terrenos
- `update(Terreno)` - Atualiza um terreno
- `delete(Long)` - Remove um terreno

**Operações de Membros:**
- `addMember(Long, String, TerrenoRole)` - Adiciona membro
- `removeMember(Long, String)` - Remove membro
- `updateMemberRole(Long, String, TerrenoRole)` - Atualiza papel
- `findMembersByTerrenoId(Long)` - Lista membros
- `findTerrenosByMemberUUID(String)` - Terrenos onde é membro

### 3. **TerrenoCommand** (`commands/TerrenoCommand.java`)
Comando completo para gerenciar terrenos:

**Subcomandos:**
- `/terreno criar <tamanho>` - Cria um terreno
- `/terreno listar` - Lista seus terrenos
- `/terreno info <id>` - Informações detalhadas
- `/terreno deletar <id>` - Remove um terreno
- `/terreno pvp <id>` - Alterna PvP
- `/terreno mobs <id>` - Alterna Mobs
- `/terreno publico <id>` - Alterna acesso público

### 4. **Configuração** (`config.yml`)
Arquivo de configuração completo com:
- Configurações de banco de dados
- Configurações de pool de conexões
- Configurações de terrenos (tamanho min/max)

## 🗄️ Estrutura do Banco de Dados

### Tabela: `terrenos`
```sql
id              BIGINT/BIGSERIAL      - ID único
dono_uuid       VARCHAR(36)           - UUID do dono
location        TEXT                  - Localização (world:x:y:z)
size            INTEGER               - Tamanho (lado do quadrado)
pvp             BOOLEAN               - PvP habilitado
mobs            BOOLEAN               - Mobs podem spawnar
public_access   BOOLEAN               - Acesso público
created_at      TIMESTAMP             - Data de criação
updated_at      TIMESTAMP             - Última atualização
```

### Tabela: `terreno_members`
```sql
id              BIGINT/BIGSERIAL      - ID único
terreno_id      BIGINT                - FK para terrenos
member_uuid     VARCHAR(36)           - UUID do membro
member_role     VARCHAR(20)           - Papel (OWNER/ADMIN/MEMBER/VISITOR)
added_at        TIMESTAMP             - Data de adição
```

**Constraints:**
- Foreign Key: `terreno_id` → `terrenos(id)` ON DELETE CASCADE
- Unique: (terreno_id, member_uuid)
- Indexes: dono_uuid, member_uuid

## 📚 Dependências Adicionadas

```xml
<!-- Pool de Conexões -->
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
    <version>5.1.0</version>
</dependency>

<!-- Drivers de Banco de Dados -->
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.47.1.0</version>
</dependency>

<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>9.1.0</version>
</dependency>

<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.4</version>
</dependency>
```

## 🚀 Como Usar

### Configuração Inicial
1. O plugin cria automaticamente o `config.yml` na primeira execução
2. Por padrão, usa SQLite (não precisa configurar nada)
3. Para usar MySQL ou PostgreSQL, edite o `config.yml`

### SQLite (Padrão)
```yaml
database:
  type: SQLITE
  sqlite:
    filename: terrenos.db
```
✅ Pronto! Funciona automaticamente.

### MySQL
```yaml
database:
  type: MYSQL
  mysql:
    host: localhost
    port: 3306
    database: jocoterrenos
    username: root
    password: sua_senha
```

### PostgreSQL
```yaml
database:
  type: POSTGRESQL
  postgresql:
    host: localhost
    port: 5432
    database: jocoterrenos
    username: postgres
    password: sua_senha
```

## 💻 Exemplo de Uso Programático

```java
// Obter o repository
JocoTerrenos plugin = JocoTerrenos.getPlugin(JocoTerrenos.class);
TerrenoRepository repository = plugin.getTerrenoRepository();

// Criar um terreno
Terreno terreno = new Terreno();
terreno.setDonoUUID(player.getUniqueId().toString());
terreno.setLocation("world:100:64:200");
terreno.setSize(10);
terreno.setPvp(false);
terreno.setMobs(true);
terreno.setPublicAccess(false);

Optional<Terreno> created = repository.create(terreno);

// Buscar terrenos de um jogador
List<Terreno> terrenos = repository.findByDonoUUID(playerUUID);

// Adicionar membro
repository.addMember(terrenoId, memberUUID, TerrenoRole.MEMBER);

// Atualizar papel
repository.updateMemberRole(terrenoId, memberUUID, TerrenoRole.ADMIN);

// Remover membro
repository.removeMember(terrenoId, memberUUID);
```

## ✨ Recursos Implementados

✅ Sistema de banco de dados multi-plataforma (SQLite, MySQL, PostgreSQL)  
✅ Pool de conexões otimizado (HikariCP)  
✅ Repository Pattern completo  
✅ CRUD completo de terrenos  
✅ Sistema de membros com papéis  
✅ Comandos de gerenciamento  
✅ Configuração flexível via YAML  
✅ Criação automática de tabelas  
✅ Tratamento de erros  
✅ Documentação completa  

## 🎯 Comandos Disponíveis

### Cercas (já existentes)
- `/cercar <tamanho>` - Cria cercas ao redor
- `/resizecerca <antigo> <novo>` - Redimensiona cercas

### Terrenos (novos)
- `/terreno criar <tamanho>` - Cria um novo terreno
- `/terreno listar` - Lista seus terrenos
- `/terreno info <id>` - Informações do terreno
- `/terreno deletar <id>` - Remove um terreno
- `/terreno pvp <id>` - Toggle PvP
- `/terreno mobs <id>` - Toggle Mobs
- `/terreno publico <id>` - Toggle acesso público

## 📋 Permissões

- `jocoterrenos.cercar` - Comando /cercar
- `jocoterrenos.resizecerca` - Comando /resizecerca
- `jocoterrenos.terreno` - Comando /terreno

## 🔧 Configurações de Pool

```yaml
database:
  pool:
    maximum-pool-size: 10      # Máximo de conexões
    minimum-idle: 2            # Mínimo de idle
    maximum-lifetime: 1800000  # Vida máxima (30 min)
    connection-timeout: 5000   # Timeout (5 seg)
    idle-timeout: 600000       # Idle timeout (10 min)
```

## 📊 Compilação

```bash
mvn clean package
```

O JAR final incluirá todas as dependências necessárias (HikariCP e drivers).

## ✅ Status

**BUILD SUCCESS** - Tudo compilado e funcionando! 🎉

O plugin está pronto para uso com:
- Sistema de banco de dados completo
- Repository funcional
- Comandos implementados
- Documentação completa

