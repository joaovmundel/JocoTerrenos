package io.github.joaovmundel.jocoTerrenos.utils;

import io.github.joaovmundel.jocoTerrenos.infrastructure.JocoLogging;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public class FenceUtils {
    private static final JocoLogging logger = new JocoLogging("io.github.joaovmundel.jocoTerrenos.utils.FenceUtils.java");

    private FenceUtils() {
    }

    /**
     * Coloca cercas em torno do jogador, definindo uma área quadrada com o tamanho especificado.
     * O jogador fica no centro da área e as cercas são posicionadas na superfície.
     *
     * @param player O jogador que estará no centro da área
     * @param areaM2 O tamanho do lado da área em blocos (será criada uma área quadrada)
     */
    public static void colocarCercas(Player player, double areaM2) {
        if (areaM2 <= 0) {
            logger.warning(player.getName() + " Tentativa de colocar cercas com área inválida: " + areaM2);
            return;
        }

        // O valor passado é o lado do quadrado
        int lado = (int) Math.ceil(areaM2);

        Location centerLoc = player.getLocation();
        World world = centerLoc.getWorld();

        if (world == null) {
            logger.warning("Mundo nulo ao tentar colocar cercas para o jogador: " + player.getName());
            return;
        }

        int centerX = centerLoc.getBlockX();
        int centerZ = centerLoc.getBlockZ();

        // Usa a função auxiliar para colocar as cercas
        int fencesPlaced = colocarCercasPerimetro(world, centerX, centerZ, lado);

        logger.info("Cercas colocadas com sucesso!");
        logger.info("§7Área: " + lado + "x" + lado + " blocos (" + (lado * lado) + "m²)");
        logger.info("§7Total de cercas colocadas: " + fencesPlaced);
    }

    /**
     * Redimensiona uma área de cercas, removendo o perímetro antigo e criando outro.
     *
     * @param centerLoc     A localização central da área
     * @param tamanhoAntigo O tamanho antigo do lado da área (ex: 10 para 10x10)
     * @param tamanhoNovo   O novo tamanho do lado da área (ex: 20 para 20x20)
     * @return Mensagem com o resultado da operação
     */
    public static String resizeCercas(Location centerLoc, int tamanhoAntigo, int tamanhoNovo) {
        if (centerLoc == null || centerLoc.getWorld() == null) {
            return getMessage("invalid-location");
        }

        if (tamanhoAntigo <= 0 || tamanhoNovo <= 0) {
            return getMessage("invalid-size");
        }

        if (tamanhoAntigo == tamanhoNovo) {
            return getMessage("resizecerca.iguais");
        }

        World world = centerLoc.getWorld();
        int centerX = centerLoc.getBlockX();
        int centerZ = centerLoc.getBlockZ();

        // Remove cercas do perímetro antigo
        int cercasRemovidas = removerCercasPerimetro(world, centerX, centerZ, tamanhoAntigo);

        // Coloca cercas no novo perímetro
        int cercasColocadas = colocarCercasPerimetro(world, centerX, centerZ, tamanhoNovo);

        String acaoKey = tamanhoNovo > tamanhoAntigo ? "action.expandida" : "action.reduzida";
        String acao = getMessage(acaoKey);

        return format("fences.resize.success",
                "acao", acao,
                "old", tamanhoAntigo,
                "oldArea", tamanhoAntigo * tamanhoAntigo,
                "new", tamanhoNovo,
                "newArea", tamanhoNovo * tamanhoNovo,
                "removed", cercasRemovidas,
                "placed", cercasColocadas
        );
    }

    /**
     * Remove cercas de uma área quadrada.
     *
     * @param centerLoc A localização central da área
     * @param tamanho   O tamanho do lado da área (ex: 10 para 10x10)
     * @return Mensagem com o resultado da operação
     */
    public static String removerCercas(Location centerLoc, int tamanho) {
        if (centerLoc == null || centerLoc.getWorld() == null) {
            return getMessage("invalid-location");
        }

        if (tamanho <= 0) {
            return getMessage("invalid-size");
        }

        World world = centerLoc.getWorld();
        int centerX = centerLoc.getBlockX();
        int centerZ = centerLoc.getBlockZ();

        // Remove cercas do perímetro
        int cercasRemovidas = removerCercasPerimetro(world, centerX, centerZ, tamanho);

        return format("fences.removed.success",
                "size", tamanho,
                "area", tamanho * tamanho,
                "count", cercasRemovidas
        );
    }

    /**
     * Coloca cercas no perímetro de uma área.
     *
     * @param world   O mundo
     * @param centerX Coordenada X central
     * @param centerZ Coordenada Z central
     * @param tamanho Tamanho do lado da área
     * @return Quantidade de cercas colocadas
     */
    private static int colocarCercasPerimetro(World world, int centerX, int centerZ, int tamanho) {
        int raio = tamanho / 2;
        int fencesPlaced = 0;

        // Coloca cercas nos 4 lados do perímetro
        for (int i = -raio; i <= raio; i++) {
            // Lado Norte (Z negativo)
            fencesPlaced += colocarCercaNoBloco(world, centerX + i, centerZ - raio);

            // Lado Sul (Z positivo)
            fencesPlaced += colocarCercaNoBloco(world, centerX + i, centerZ + raio);

            // Lado Oeste (X negativo) - evita duplicar os cantos
            if (i != -raio && i != raio) {
                fencesPlaced += colocarCercaNoBloco(world, centerX - raio, centerZ + i);
            }

            // Lado Leste (X positivo) - evita duplicar os cantos
            if (i != -raio && i != raio) {
                fencesPlaced += colocarCercaNoBloco(world, centerX + raio, centerZ + i);
            }
        }

        return fencesPlaced;
    }

    /**
     * Remove cercas do perímetro de uma área.
     *
     * @param world   O mundo
     * @param centerX Coordenada X central
     * @param centerZ Coordenada Z central
     * @param tamanho Tamanho do lado da área
     * @return Quantidade de cercas removidas
     */
    private static int removerCercasPerimetro(World world, int centerX, int centerZ, int tamanho) {
        int raio = tamanho / 2;
        int fencesRemoved = 0;

        // Remove cercas nos 4 lados do perímetro
        for (int i = -raio; i <= raio; i++) {
            // Lado Norte (Z negativo)
            fencesRemoved += removerCercaNoBloco(world, centerX + i, centerZ - raio);

            // Lado Sul (Z positivo)
            fencesRemoved += removerCercaNoBloco(world, centerX + i, centerZ + raio);

            // Lado Oeste (X negativo) - evita duplicar os cantos
            if (i != -raio && i != raio) {
                fencesRemoved += removerCercaNoBloco(world, centerX - raio, centerZ + i);
            }

            // Lado Leste (X positivo) - evita duplicar os cantos
            if (i != -raio && i != raio) {
                fencesRemoved += removerCercaNoBloco(world, centerX + raio, centerZ + i);
            }
        }

        return fencesRemoved;
    }

    /**
     * Remove uma cerca numa posição X, Z específica se for uma cerca.
     *
     * @param world O mundo onde a cerca será removida
     * @param x     Coordenada X
     * @param z     Coordenada Z
     * @return 1 se a cerca foi removida, 0 caso contrário
     */
    private static int removerCercaNoBloco(World world, int x, int z) {
        int y = encontrarSuperficie(world, x, z);
        if (y == -1) {
            return 0;
        }
        int removed = 0;
        int maxY = world.getMaxHeight() - 1;
        int minY = world.getMinHeight();
        Material fenceMat = getConfiguredFenceMaterial();
        Block atSurface = world.getBlockAt(x, y, z);
        if (atSurface.getType() == fenceMat) {
            atSurface.setType(Material.AIR);
            removed++;
        }
        for (int yy = y + 1; yy <= Math.min(y + 3, maxY); yy++) {
            Block above = world.getBlockAt(x, yy, z);
            if (above.getType() == fenceMat) {
                above.setType(Material.AIR);
                removed++;
            }
        }
        for (int yy = y - 1; yy >= minY; yy--) {
            Block below = world.getBlockAt(x, yy, z);
            if (below.getType() == fenceMat) {
                below.setType(Material.AIR);
                removed++;
            }
        }
        return removed;
    }

    /**
     * Coloca uma cerca numa posição X, Z específica.
     * Busca a superfície adequada para colocar a cerca.
     *
     * @param world O mundo onde a cerca será colocada
     * @param x     Coordenada X
     * @param z     Coordenada Z
     * @return 1 se a cerca foi colocada, 0 caso contrário
     */
    private static int colocarCercaNoBloco(World world, int x, int z) {
        int y = encontrarSuperficie(world, x, z);

        if (y == -1) {
            return 0; // Não foi possível encontrar uma superfície adequada
        }

        Block block = world.getBlockAt(x, y, z);
        Material fenceMat = getConfiguredFenceMaterial();

        // Verifica se o bloco já é uma cerca
        if (block.getType() == fenceMat) {
            return 0;
        }

        // Coloca a cerca
        block.setType(fenceMat);
        return 1;
    }

    /**
     * Encontra a superfície adequada para colocar a cerca.
     * Procura de cima para baixo, evitando cavernas e priorizando o solo.
     *
     * @param world O mundo
     * @param x     Coordenada X
     * @param z     Coordenada Z
     * @return A coordenada Y da superfície, ou --1 se não encontrar
     */
    private static int encontrarSuperficie(World world, int x, int z) {
        int maxY = world.getMaxHeight() - 1;
        int minY = world.getMinHeight();

        // Começa do topo e desce até encontrar um bloco sólido
        for (int y = maxY; y > minY; y--) {
            Block blocoAtual = world.getBlockAt(x, y, z);
            Block blocoAbaixo = world.getBlockAt(x, y - 1, z);

            // Verifica se o bloco abaixo é sólido e o atual está vazio
            if (isBlocoSolido(blocoAbaixo) && !isBlocoSolido(blocoAtual)) {
                // Verifica se não é uma caverna (há céu acima)
                if (temCeuAcima(world, x, y, z)) {
                    return y;
                }
            }
        }

        // Se não encontrou com céu acima, retorna a superfície mais alta disponível
        for (int y = maxY; y > minY; y--) {
            Block blocoAtual = world.getBlockAt(x, y, z);
            Block blocoAbaixo = world.getBlockAt(x, y - 1, z);

            if (isBlocoSolido(blocoAbaixo) && !isBlocoSolido(blocoAtual)) {
                return y;
            }
        }

        return -1;
    }

    /**
     * Verifica se há céu/ar acima de uma determinada posição.
     * Isso ajuda a evitar colocar cercas dentro de cavernas.
     *
     * @param world O mundo
     * @param x     Coordenada X
     * @param y     Coordenada Y inicial
     * @param z     Coordenada Z
     * @return true se há céu acima, false caso contrário
     */
    private static boolean temCeuAcima(World world, int x, int y, int z) {
        int maxY = world.getMaxHeight() - 1;
        int blocosAr = 0;

        // Verifica se há pelo menos 3 blocos de ar consecutivos acima
        for (int checkY = y; checkY < maxY && checkY < y + 10; checkY++) {
            Block block = world.getBlockAt(x, checkY, z);
            if (!isBlocoSolido(block)) {
                blocosAr++;
                if (blocosAr >= 3) {
                    return true;
                }
            } else {
                blocosAr = 0;
            }
        }

        return false;
    }

    /**
     * Verifica se um bloco é sólido.
     *
     * @param block O bloco a ser verificado
     * @return true se o bloco é sólido, false caso contrário
     */
    private static boolean isBlocoSolido(Block block) {
        Material material = block.getType();

        // Verifica se não é ar, água, lava ou outros blocos não sólidos
        return material.isSolid() &&
                material != Material.AIR &&
                material != Material.CAVE_AIR &&
                material != Material.VOID_AIR;
    }

    private static Material getConfiguredFenceMaterial() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("JocoTerrenos");
        if (plugin == null) return Material.OAK_FENCE;
        String matName = plugin.getConfig().getString("lands.fence-material", plugin.getConfig().getString("terrenos.fence-material", "OAK_FENCE"));
        try {
            return Material.valueOf(matName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Material.OAK_FENCE;
        }
    }

    private static String getMessage(String key) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("JocoTerrenos");
        if (plugin != null) {
            try {
                java.lang.reflect.Method m = plugin.getClass().getMethod("getMessageService");
                Object svc = m.invoke(plugin);
                if (svc != null) {
                    java.lang.reflect.Method get = svc.getClass().getMethod("get", String.class);
                    return (String) get.invoke(svc, key);
                }
            } catch (Throwable ignored) {
            }
        }
        // Fallback simples à própria chave
        return key;
    }

    private static String format(String key, Object... kv) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("JocoTerrenos");
        if (plugin != null) {
            try {
                java.lang.reflect.Method m = plugin.getClass().getMethod("getMessageService");
                Object svc = m.invoke(plugin);
                if (svc != null) {
                    // constrói placeholders map via helper estático
                    java.lang.reflect.Method ph = svc.getClass().getMethod("placeholders", Object[].class);
                    Object map = ph.invoke(null, (Object) kv);
                    java.lang.reflect.Method fmt = svc.getClass().getMethod("format", String.class, java.util.Map.class);
                    return (String) fmt.invoke(svc, key, (Map) map);
                }
            } catch (Throwable ignored) {
            }
        }
        return key;
    }

}
