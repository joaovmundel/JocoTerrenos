# Guia de Testes - JocoTerrenos

## 🧪 Testes no Servidor

### 1. Teste de Inicialização

**Passo a passo:**
1. Coloque o JAR na pasta `plugins`
2. Inicie o servidor
3. Verifique os logs:

```
[JocoTerrenos] Conexão com o banco de dados SQLITE estabelecida com sucesso!
[JocoTerrenos] Tabelas do banco de dados criadas/verificadas com sucesso!
[JocoTerrenos] JocoTerrenos habilitado com sucesso!
```

✅ **Resultado esperado:** Plugin inicializa sem erros e cria o arquivo `terrenos.db`

### 2. Teste de Criação de Terreno

**No jogo:**
```
/terreno criar 10
```

✅ **Resultado esperado:**
```
Terreno criado com sucesso!
ID: 1
Tamanho: 10x10
Localização: world:123.45:64.00:678.90
```

### 3. Teste de Listagem de Terrenos

**No jogo:**
```
/terreno listar
```

✅ **Resultado esperado:**
```
=== Seus Terrenos ===
#1 - Tamanho: 10x10 - PvP: OFF - Mobs: ON
```

### 4. Teste de Informações do Terreno

**No jogo:**
```
/terreno info 1
```

✅ **Resultado esperado:**
```
=== Informações do Terreno #1 ===
Tamanho: 10x10
Localização: world:123.45:64.00:678.90
PvP: Desabilitado
Mobs: Habilitado
Acesso Público: Não
Membros: 0
```

### 5. Teste de Toggle PvP

**No jogo:**
```
/terreno pvp 1
```

✅ **Resultado esperado:**
```
PvP habilitado!
Terreno atualizado!
```

### 6. Teste de Toggle Mobs

**No jogo:**
```
/terreno mobs 1
```

✅ **Resultado esperado:**
```
Mobs desabilitados!
Terreno atualizado!
```

### 7. Teste de Toggle Acesso Público

**No jogo:**
```
/terreno publico 1
```

✅ **Resultado esperado:**
```
Acesso público habilitado!
Terreno atualizado!
```

### 8. Teste de Deletar Terreno

**No jogo:**
```
/terreno deletar 1
```

✅ **Resultado esperado:**
```
Terreno deletado com sucesso!
```

### 9. Teste de Cercas

**No jogo:**
```
/cercar 10
```

✅ **Resultado esperado:**
```
Cercas colocadas com sucesso!
Área: 10x10 blocos (100m²)
Total de cercas colocadas: 40
```

### 10. Teste de Resize de Cercas

**No jogo:**
```
/resizecerca 10 20
```

✅ **Resultado esperado:**
```
Área expandida com sucesso!
Tamanho antigo: 10x10 (100m²)
Tamanho novo: 20x20 (400m²)
Cercas removidas: X
Cercas colocadas: Y
```

## 🔧 Testes de Configuração

### Teste 1: Mudar para MySQL

1. Pare o servidor
2. Edite `plugins/JocoTerrenos/config.yml`:
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
3. Inicie o servidor
4. Verifique os logs:
```
[JocoTerrenos] Conexão com o banco de dados MYSQL estabelecida com sucesso!
```

### Teste 2: Verificar Persistência

1. Crie alguns terrenos
2. Pare o servidor
3. Inicie o servidor novamente
4. Liste os terrenos com `/terreno listar`

✅ **Resultado esperado:** Todos os terrenos criados ainda estão lá

## 🗄️ Testes de Banco de Dados

### Verificar SQLite

**Com SQLite Browser:**
1. Abra o arquivo `plugins/JocoTerrenos/terrenos.db`
2. Verifique as tabelas `terrenos` e `terreno_members`
3. Crie um terreno no jogo
4. Atualize a visualização do banco
5. Veja o registro criado

### Verificar MySQL

**No MySQL Workbench ou phpMyAdmin:**
```sql
USE jocoterrenos;

-- Ver todos os terrenos
SELECT * FROM terrenos;

-- Ver todos os membros
SELECT * FROM terreno_members;

-- Ver terrenos com seus membros
SELECT t.id, t.size, tm.member_uuid, tm.member_role
FROM terrenos t
LEFT JOIN terreno_members tm ON t.id = tm.terreno_id;
```

## 🐛 Testes de Erro

### Teste 1: Criar terreno com tamanho inválido
```
/terreno criar 1000
```
✅ **Resultado esperado:** `Tamanho máximo: 100`

### Teste 2: Criar terreno muito pequeno
```
/terreno criar 1
```
✅ **Resultado esperado:** `Tamanho mínimo: 5`

### Teste 3: Deletar terreno de outro jogador
```
/terreno deletar 1
```
✅ **Resultado esperado:** `Você não é o dono deste terreno!`

### Teste 4: Info de terreno inexistente
```
/terreno info 999
```
✅ **Resultado esperado:** `Terreno não encontrado!`

### Teste 5: Comando inválido
```
/terreno xpto
```
✅ **Resultado esperado:** Exibe o help com todos os comandos

## 📊 Teste de Performance

### Pool de Conexões

1. Configure um pool pequeno:
```yaml
database:
  pool:
    maximum-pool-size: 2
    minimum-idle: 1
```

2. Execute múltiplos comandos rapidamente:
```
/terreno criar 10
/terreno criar 15
/terreno criar 20
/terreno listar
/terreno info 1
/terreno info 2
/terreno info 3
```

✅ **Resultado esperado:** Todos os comandos funcionam sem erro de timeout

### Múltiplos Jogadores

1. Tenha 2+ jogadores online
2. Cada um cria terrenos simultaneamente
3. Cada um lista seus terrenos

✅ **Resultado esperado:** 
- Cada jogador vê apenas seus terrenos
- Não há conflitos ou dados corrompidos

## 🔄 Teste de Migração

### SQLite → MySQL

1. Inicie com SQLite
2. Crie alguns terrenos
3. Pare o servidor
4. Exporte os dados do SQLite
5. Configure MySQL no config.yml
6. Importe os dados no MySQL
7. Inicie o servidor

✅ **Resultado esperado:** Todos os dados preservados

## ✅ Checklist de Testes

- [ ] Plugin inicializa sem erros
- [ ] Banco de dados SQLite funciona
- [ ] Banco de dados MySQL funciona (se configurado)
- [ ] Banco de dados PostgreSQL funciona (se configurado)
- [ ] Comando `/terreno criar` funciona
- [ ] Comando `/terreno listar` funciona
- [ ] Comando `/terreno info` funciona
- [ ] Comando `/terreno deletar` funciona
- [ ] Comando `/terreno pvp` funciona
- [ ] Comando `/terreno mobs` funciona
- [ ] Comando `/terreno publico` funciona
- [ ] Comando `/cercar` funciona
- [ ] Comando `/resizecerca` funciona
- [ ] Persistência de dados funciona
- [ ] Validações de tamanho funcionam
- [ ] Validações de permissão funcionam
- [ ] Pool de conexões funciona sob carga
- [ ] Múltiplos jogadores funcionam simultaneamente
- [ ] Dados não são corrompidos
- [ ] Logs estão claros e informativos

## 📝 Relatório de Bugs

Ao encontrar um bug, anote:
1. **Comando executado**
2. **Resultado esperado**
3. **Resultado obtido**
4. **Logs de erro** (se houver)
5. **Configuração do banco de dados**
6. **Versão do servidor**

## 🎯 Casos de Uso Reais

### Caso 1: Jogador cria seu primeiro terreno
```
1. /terreno criar 10
2. /cercar 10
3. /terreno info 1
```

### Caso 2: Jogador gerencia múltiplos terrenos
```
1. /terreno criar 10
2. /terreno criar 20
3. /terreno criar 15
4. /terreno listar
5. /terreno pvp 1
6. /terreno mobs 2
```

### Caso 3: Admin verifica banco de dados
```
1. Conecta ao banco
2. SELECT COUNT(*) FROM terrenos;
3. SELECT COUNT(*) FROM terreno_members;
4. Verifica integridade dos dados
```

## 🔍 Monitoramento

### Logs Importantes

No console, procure por:
- `Conexão com o banco de dados X estabelecida`
- `Tabelas do banco de dados criadas/verificadas`
- `Terreno criado com ID: X`
- `Terreno atualizado: X`
- `Terreno deletado: X`

### Erros Comuns

Se aparecer:
- `Erro ao conectar com o banco de dados` → Verifique config.yml
- `SQLException` → Problema na query ou conexão
- `NullPointerException` → Dado não encontrado

## ✨ Conclusão

Todos os testes passando = Sistema funcionando perfeitamente! 🎉

