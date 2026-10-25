package io.github.joaovmundel.jocoTerrenos.utils;

import org.bukkit.Location;

public class LocationUtils {
    public static String formattedLocation(String loc) {
        String[] parts = loc.split(":");
        if (parts.length < 4) {
            return loc;
        }
        int x = (int) Math.floor(parseLocaleDouble(parts[1]));
        int y = (int) Math.floor(parseLocaleDouble(parts[2]));
        int z = (int) Math.floor(parseLocaleDouble(parts[3]));
        return x + ", " + y + ", " + z;
    }

    private static double parseLocaleDouble(String s) {
        String normalized = s.replace(',', '.').trim();
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Formata a localização para "string"
     */
    public static String formatarLocalizacao(Location loc) {
        return String.format("%s:%.2f:%.2f:%.2f",
                loc.getWorld() != null ? loc.getWorld().getName() : "world",
                loc.getX(),
                loc.getY(),
                loc.getZ()
        );
    }

    /**
     * Converte uma "string" de localização para objeto Location
     * Formato esperado: "world:x:y:z"
     */
    public static Location converterLocalizacao(String locationStr) {
        LocationRaw raw = converterLocalizacaoRaw(locationStr);
        if (raw == null) {
            return null;
        }
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(raw.worldName());
        if (world == null) {
            return null;
        }
        return new Location(world, raw.x(), raw.y(), raw.z());
    }

    /**
     * Versão "raw" que não usa Bukkit APIs, segura para threads assíncronas.
     * Retorna null se o formato for inválido.
     */
    public static LocationRaw converterLocalizacaoRaw(String locationStr) {
        if (locationStr == null || locationStr.isEmpty()) {
            return null;
        }
        String[] parts = locationStr.split(":");
        if (parts.length != 4) {
            return null;
        }
        return new LocationRaw(parts[0].trim(), parseLocaleDouble(parts[1]), parseLocaleDouble(parts[2]), parseLocaleDouble(parts[3]));
    }

    /**
     * DTO simples para parsing sem Bukkit. Usa os mesmos acessores de um {@code record} (Java 16+), que
     * não existe no Java 7.
     */
    public static final class LocationRaw {
        private final String worldName;
        private final double x;
        private final double y;
        private final double z;

        public LocationRaw(String worldName, double x, double y, double z) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String worldName() {
            return worldName;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }
    }
}
