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
     * Formata a localização para string
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
     * Parseia uma string de localização para objeto Location
     * Formato esperado: "world:x:y:z"
     */
    public static Location parsearLocalizacao(String locationStr) {
        if (locationStr == null || locationStr.isEmpty()) {
            return null;
        }

        try {
            String[] parts = locationStr.split(":");
            if (parts.length != 4) {
                return null;
            }

            String worldName = parts[0];
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);

            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) {
                return null;
            }

            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }
}
