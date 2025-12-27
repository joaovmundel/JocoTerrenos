# Nova Funcionalidade: Remover Cercas

## 🎯 Objetivo

Adicionar a capacidade de remover cercas de uma área específica, passando a localização do centro e o tamanho da área.

## ✅ O que foi implementado

### 1. **Método FenceUtils.removerCercas()**
```java
public static String removerCercas(Location centerLoc, int tamanho)
```

**Parâmetros:**
- `centerLoc` - Localização central da área (Location)
- `tamanho` - Tamanho do lado da área (ex: 10 = 10x10)

**Retorno:**
- String com mensagem formatada sobre o resultado da operação

**Funcionalidade:**
- Remove todas as cercas (OAK_FENCE) do perímetro de uma área quadrada
- Busca cercas na superfície, evitando cavernas
- Retorna mensagem com estatísticas da operação

### 2. **Comando /removercerca**
```
/removercerca <tamanho>
```

**Exemplos:**
- `/removercerca 10` - Remove cercas de área 10x10
- `/removercerca 20` - Remove cercas de área 20x20
- `/removercerca 50` - Remove cercas de área 50x50

**Como funciona:**
1. Jogador executa o comando com o tamanho desejado
2. Sistema usa a localização atual do jogador como centro
3. Remove todas as cercas no perímetro da área especificada
4. Exibe mensagem com resultado

## 📊 Fluxo de Execução

```
1. Jogador: /removercerca 10
   ↓
2. RemoverCercaCommand.onCommand()
   ├─ Valida se é um jogador
   ├─ Valida se tem argumentos
   ├─ Converte argumento para número
   └─ Obtém localização do jogador
   ↓
3. FenceUtils.removerCercas(location, 10)
   ├─ Valida localização
   ├─ Valida tamanho
   ├─ Obtém coordenadas do centro
   └─ Chama removerCercasPerimetro()
   ↓
4. removerCercasPerimetro(world, x, z, tamanho)
   ├─ Calcula raio (tamanho / 2)
   ├─ Loop pelos 4 lados do perímetro
   │   ├─ Lado Norte (Z-)
   │   ├─ Lado Sul (Z+)
   │   ├─ Lado Oeste (X-)
   │   └─ Lado Leste (X+)
   └─ Conta cercas removidas
   ↓
5. removerCercaNoBloco(world, x, z)
   ├─ Encontra superfície no X, Z
   ├─ Verifica se é cerca (OAK_FENCE)
   ├─ Remove cerca (setType AIR)
   └─ Retorna 1 ou 0
   ↓
6. Retorna para o Jogador:
   "Cercas removidas com sucesso!
    Área: 10x10 blocos (100m²)
    Total de cercas removidas: 40"
```

## 🔧 Detalhes Técnicos

### Lógica de Remoção
```java
private static int removerCercaNoBloco(World world, int x, int z) {
    // Busca a superfície adequada
    int y = encontrarSuperficie(world, x, z);
    
    if (y == -1) return 0;
    
    Block block = world.getBlockAt(x, y, z);
    
    // Verifica se o bloco é uma cerca de carvalho
    if (block.getType() == Material.OAK_FENCE) {
        block.setType(Material.AIR);
        return 1;
    }
    
    return 0;
}
```

### Perímetro Quadrado
```
Para tamanho = 10 (área 10x10):
- Raio = 5
- Centro em (0, 0)
- Cerca de X=-5 a X=5, Z=-5 e Z=5 (Norte e Sul)
- Cerca de Z=-4 a Z=4, X=-5 e X=5 (Oeste e Leste)
- Total: 40 cercas (10+10+8+8)

Exemplo visual (10x10):
  -5  -4  -3  -2  -1   0  +1  +2  +3  +4  +5
-5 F   F   F   F   F   F   F   F   F   F   F
-4 F   .   .   .   .   .   .   .   .   .   F
-3 F   .   .   .   .   .   .   .   .   .   F
-2 F   .   .   .   .   .   .   .   .   .   F
-1 F   .   .   .   .   .   .   .   .   .   F
 0 F   .   .   .   .   P   .   .   .   .   F
+1 F   .   .   .   .   .   .   .   .   .   F
+2 F   .   .   .   .   .   .   .   .   .   F
+3 F   .   .   .   .   .   .   .   .   .   F
+4 F   .   .   .   .   .   .   .   .   .   F
+5 F   F   F   F   F   F   F   F   F   F   F

F = Fence (removida)
P = Player (centro)
. = Área interna (não afetada)
```

## 🎮 Casos de Uso

### Caso 1: Remover terreno temporário
```bash
# Jogador criou um terreno de teste
/cercar 10

# Decide remover
/removercerca 10
```

### Caso 2: Limpar área antes de reconstruir
```bash
# Área antiga de 20x20
/removercerca 20

# Criar nova de tamanho diferente
/cercar 30
```

### Caso 3: Integração com sistema de terrenos
```java
// No comando deletar terreno
public boolean handleDeletar(Player player, Long terrenoId) {
    Optional<Terreno> terreno = service.buscarTerreno(terrenoId);
    
    if (terreno.isPresent()) {
        // Remove cercas do terreno
        Location loc = parseLocation(terreno.get().getLocation());
        FenceUtils.removerCercas(loc, terreno.get().getSize());
        
        // Deleta do banco
        service.deletarTerreno(terrenoId, player.getUniqueId().toString());
    }
}
```

## 🔄 Comparação com Outros Métodos

### colocarCercas() vs removerCercas()
```java
// Colocar cercas
FenceUtils.colocarCercas(player, 10);
// - Coloca cercas OAK_FENCE
// - Retorna void (envia mensagens ao player)
// - Recebe Player como parâmetro

// Remover cercas
String result = FenceUtils.removerCercas(location, 10);
// - Remove cercas OAK_FENCE
// - Retorna String (mensagem formatada)
// - Recebe Location como parâmetro
```

### resizeCercas() - Usa ambos internamente
```java
// Resize de 10x10 para 20x20
FenceUtils.resizeCercas(location, 10, 20);
// 1. Remove cercas antigas (10x10)
// 2. Coloca cercas novas (20x20)
```

## 📝 Validações

### Validação de Localização
```java
if (centerLoc == null || centerLoc.getWorld() == null) {
    return "§cLocalização inválida!";
}
```

### Validação de Tamanho
```java
if (tamanho <= 0) {
    return "§cO tamanho deve ser maior que zero!";
}
```

### Validação de Material
```java
if (block.getType() == Material.OAK_FENCE) {
    block.setType(Material.AIR);
    return 1;
}
```

## 💡 Melhorias Futuras

### 1. Suporte a múltiplos tipos de cerca
```java
private static final Material[] FENCE_TYPES = {
    Material.OAK_FENCE,
    Material.SPRUCE_FENCE,
    Material.BIRCH_FENCE,
    Material.JUNGLE_FENCE,
    Material.ACACIA_FENCE,
    Material.DARK_OAK_FENCE
};

private static boolean isFence(Material material) {
    return Arrays.asList(FENCE_TYPES).contains(material);
}
```

### 2. Modo de confirmação
```java
// Primeiro comando: preview
/removercerca 10 preview
// Mostra quantas cercas seriam removidas

// Segundo comando: confirma
/removercerca 10 confirmar
// Realmente remove
```

### 3. Modo seletivo
```java
// Remove apenas de um lado
/removercerca 10 norte
/removercerca 10 sul
/removercerca 10 leste
/removercerca 10 oeste
```

### 4. Undo/Redo
```java
// Salvar estado anterior
Map<Location, Material> previousState = new HashMap<>();

// Permitir desfazer
/removercerca undo
```

## ✅ Testes

### Teste 1: Remoção básica
```
1. Criar cercas: /cercar 10
2. Remover cercas: /removercerca 10
3. Verificar: todas as cercas removidas
```

### Teste 2: Tamanhos diferentes
```
1. /cercar 20
2. /removercerca 20
3. /cercar 10
4. /removercerca 10
```

### Teste 3: Validações
```
1. /removercerca -5  → "tamanho deve ser maior que zero"
2. /removercerca abc → "número válido"
3. /removercerca     → "uso correto"
```

### Teste 4: Integração com terrenos
```
1. /terreno criar 15
2. /removercerca 15
3. /terreno info <id> → cercas removidas mas terreno ainda existe
```

## 📊 Estatísticas

Para diferentes tamanhos de área:

| Tamanho | Área (m²) | Cercas Removidas |
|---------|-----------|------------------|
| 5x5     | 25        | 20               |
| 10x10   | 100       | 40               |
| 20x20   | 400       | 80               |
| 50x50   | 2500      | 200              |
| 100x100 | 10000     | 400              |

**Fórmula:** `cercasRemovidas = (tamanho * 4) - 4`

## 🎯 Resumo

✅ **Método criado:** `FenceUtils.removerCercas(Location, int)`  
✅ **Comando criado:** `/removercerca <tamanho>`  
✅ **Registrado no plugin:** JocoTerrenos.java  
✅ **Registrado no config:** plugin.yml  
✅ **Documentado:** README.md  
✅ **Compilado:** BUILD SUCCESS  

**Status:** Pronto para uso! 🎉

