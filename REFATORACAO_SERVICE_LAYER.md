# Refatoração: Service Layer Pattern

## 🎯 O que foi feito

A lógica de negócio foi movida dos **Commands** para um **Service Layer**, seguindo as melhores práticas de arquitetura de software.

## 📊 Arquitetura Antes vs Depois

### ❌ Antes (Anti-pattern)
```
Command (TerrenoCommand)
    ├─ Validações de negócio
    ├─ Criação de objetos
    ├─ Formatação de dados
    ├─ Chamadas diretas ao Repository
    └─ Lógica de toggle
```

**Problemas:**
- Command com muita responsabilidade
- Lógica de negócio misturada com lógica de apresentação
- Difícil de testar
- Difícil de reutilizar a lógica em outros lugares
- Viola o princípio da Responsabilidade Única (SRP)

### ✅ Depois (Clean Architecture)
```
Command (TerrenoCommand)
    ├─ Recebe parâmetros
    ├─ Valida entrada básica
    ├─ Delega para Service
    └─ Exibe resultado
        ↓
Service (TerrenoService)
    ├─ Validações de negócio
    ├─ Lógica de domínio
    ├─ Orquestração de operações
    └─ Chamadas ao Repository
        ↓
Repository (TerrenoRepository)
    ├─ Acesso ao banco de dados
    ├─ Queries SQL
    └─ Mapeamento de dados
```

**Benefícios:**
- Separação clara de responsabilidades
- Código reutilizável
- Fácil de testar unitariamente
- Fácil de manter e evoluir
- Segue SOLID principles

## 📁 Estrutura de Arquivos

```
src/main/java/io/github/joaovmundel/jocoTerrenos/
├── commands/
│   └── TerrenoCommand.java          ← Apenas apresentação
├── service/
│   ├── TerrenoService.java          ← ✨ NOVA - Lógica de negócio
│   └── FenceUtils.java              ← Utilitário de cercas
├── repositories/
│   └── TerrenoRepository.java       ← Acesso a dados
└── models/
    ├── Terreno.java                 ← Entidade
    ├── TerrenoMember.java           ← Entidade
    └── TerrenoRole.java             ← Enum
```

## 🔧 TerrenoService - Métodos

### Criação e Busca
```java
Optional<Terreno> criarTerreno(Player player, int tamanho)
List<Terreno> listarTerrenosDoJogador(String playerUUID)
Optional<Terreno> buscarTerreno(Long id)
```

### Gerenciamento
```java
boolean deletarTerreno(Long id, String playerUUID)
boolean isDono(Long terrenoId, String playerUUID)
```

### Toggle de Configurações
```java
boolean togglePvp(Long id, String playerUUID)
boolean toggleMobs(Long id, String playerUUID)
boolean togglePublico(Long id, String playerUUID)
```

### Configurações
```java
int getTamanhoMinimo()
int getTamanhoMaximo()
```

## 📝 Exemplo de Uso

### Antes (no Command)
```java
// ❌ Muita lógica no Command
Location loc = player.getLocation();
String location = String.format("%s:%.2f:%.2f:%.2f",
    loc.getWorld().getName(),
    loc.getX(),
    loc.getY(),
    loc.getZ()
);

Terreno terreno = new Terreno();
terreno.setDonoUUID(player.getUniqueId().toString());
terreno.setLocation(location);
terreno.setSize(tamanho);
terreno.setPvp(false);
terreno.setMobs(true);
terreno.setPublicAccess(false);

Optional<Terreno> created = repository.create(terreno);
```

### Depois (com Service)
```java
// ✅ Limpo e simples no Command
Optional<Terreno> created = terrenoService.criarTerreno(player, tamanho);

if (created.isPresent()) {
    FenceUtils.colocarCercas(player, tamanho);
    // Exibe mensagens...
}
```

## 🎯 Responsabilidades Definidas

### TerrenoCommand (Presentation Layer)
- ✅ Receber argumentos do comando
- ✅ Validar formato dos parâmetros (números, IDs)
- ✅ Chamar o Service apropriado
- ✅ Exibir mensagens para o jogador
- ❌ NÃO contém lógica de negócio
- ❌ NÃO acessa Repository diretamente

### TerrenoService (Business Layer)
- ✅ Validações de regras de negócio
- ✅ Lógica de domínio (criar, atualizar, deletar)
- ✅ Orquestração de múltiplas operações
- ✅ Verificação de permissões
- ✅ Formatação de dados de domínio
- ❌ NÃO conhece detalhes do Command
- ❌ NÃO lida com interface do usuário

### TerrenoRepository (Data Layer)
- ✅ Queries ao banco de dados
- ✅ Mapeamento objeto-relacional
- ✅ CRUD básico
- ❌ NÃO contém lógica de negócio
- ❌ NÃO conhece o domínio da aplicação

## 🧪 Testabilidade

### Antes
```java
// ❌ Difícil de testar - depende de Player, Command, etc
@Test
void testCriarTerreno() {
    // Precisa mockar Player, Command, CommandSender...
    // Lógica misturada dificulta testes unitários
}
```

### Depois
```java
// ✅ Fácil de testar - service isolado
@Test
void testCriarTerreno() {
    TerrenoRepository mockRepo = mock(TerrenoRepository.class);
    FileConfiguration mockConfig = mock(FileConfiguration.class);
    TerrenoService service = new TerrenoService(mockRepo, mockConfig);
    
    Player mockPlayer = mock(Player.class);
    Optional<Terreno> result = service.criarTerreno(mockPlayer, 10);
    
    assertTrue(result.isPresent());
}
```

## 🔄 Fluxo de Execução

### Exemplo: `/terreno criar 10`

```
1. TerrenoCommand.onCommand()
   ├─ Recebe args = ["criar", "10"]
   ├─ Valida que tem 2 argumentos
   └─ Chama handleCriar(player, args)

2. TerrenoCommand.handleCriar()
   ├─ Converte "10" para int
   ├─ Valida tamanho mínimo via service
   ├─ Valida tamanho máximo via service
   └─ Chama terrenoService.criarTerreno(player, 10)

3. TerrenoService.criarTerreno()
   ├─ Valida regras de negócio
   ├─ Obtém localização do player
   ├─ Formata localização
   ├─ Cria objeto Terreno
   ├─ Define valores padrão
   └─ Chama repository.create(terreno)

4. TerrenoRepository.create()
   ├─ Prepara SQL INSERT
   ├─ Executa no banco
   ├─ Obtém ID gerado
   └─ Retorna Optional<Terreno>

5. Volta para TerrenoCommand.handleCriar()
   ├─ Chama FenceUtils.colocarCercas()
   ├─ Exibe mensagens de sucesso
   └─ Retorna true
```

## 📈 Melhorias Futuras

Com o Service Layer em vigor, agora é fácil adicionar:

### Cache
```java
public class TerrenoService {
    private final Map<Long, Terreno> cache = new HashMap<>();
    
    public Optional<Terreno> buscarTerreno(Long id) {
        if (cache.containsKey(id)) {
            return Optional.of(cache.get(id));
        }
        Optional<Terreno> terreno = repository.findById(id);
        terreno.ifPresent(t -> cache.put(id, t));
        return terreno;
    }
}
```

### Eventos
```java
public Optional<Terreno> criarTerreno(Player player, int tamanho) {
    Optional<Terreno> terreno = // ... lógica de criação
    
    if (terreno.isPresent()) {
        plugin.getServer().getPluginManager()
            .callEvent(new TerrenoCreatedEvent(terreno.get()));
    }
    
    return terreno;
}
```

### Logs Detalhados
```java
public boolean deletarTerreno(Long id, String playerUUID) {
    logger.info("Tentando deletar terreno " + id + " por " + playerUUID);
    boolean success = repository.delete(id);
    
    if (success) {
        logger.info("Terreno " + id + " deletado com sucesso");
    } else {
        logger.warning("Falha ao deletar terreno " + id);
    }
    
    return success;
}
```

### Transações
```java
@Transactional
public boolean transferirTerreno(Long terrenoId, String novoDonoUUID) {
    Optional<Terreno> terreno = repository.findById(terrenoId);
    if (terreno.isEmpty()) return false;
    
    Terreno t = terreno.get();
    String antigoDonoUUID = t.getDonoUUID();
    
    t.setDonoUUID(novoDonoUUID);
    boolean updated = repository.update(t);
    
    if (updated) {
        // Remove permissões do antigo dono
        repository.removeMember(terrenoId, antigoDonoUUID);
        // Adiciona novo dono como OWNER
        repository.addMember(terrenoId, novoDonoUUID, TerrenoRole.OWNER);
    }
    
    return updated;
}
```

## ✅ Conclusão

A refatoração para Service Layer traz:

- 🎯 **Separação de Responsabilidades** - Cada camada faz uma coisa
- 🧪 **Testabilidade** - Fácil criar testes unitários
- 🔄 **Reutilização** - Lógica pode ser usada em APIs, outros comandos, etc
- 📚 **Manutenibilidade** - Mais fácil encontrar e modificar código
- 🚀 **Escalabilidade** - Fácil adicionar novas features
- 📖 **Legibilidade** - Código mais limpo e compreensível

**Status:** ✅ Compilado e funcionando!

