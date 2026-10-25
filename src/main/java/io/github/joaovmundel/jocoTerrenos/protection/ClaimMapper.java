package io.github.joaovmundel.jocoTerrenos.protection;

import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.models.TerrenoMember;
import io.github.joaovmundel.jocoTerrenos.models.TerrenoRole;
import io.github.joaovmundel.jocoTerrenos.utils.LocationUtils;
import io.github.joaovmundel.jocoTerrenos.utils.LocationUtils.LocationRaw;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns a stored {@link Terreno} into the {@link Claim} the protection works with.
 */
public final class ClaimMapper {

    private ClaimMapper() {
    }

    /**
     * @return the claim, or {@code null} when the stored location or size cannot be understood (the caller
     * decides how loudly to complain)
     */
    public static Claim toClaim(Terreno terreno) {
        if (terreno.getId() == null || terreno.getDonoUUID() == null || terreno.getSize() == null || terreno.getSize() <= 0) {
            return null;
        }
        LocationRaw center = LocationUtils.converterLocalizacaoRaw(terreno.getLocation());
        if (center == null || center.worldName() == null || center.worldName().isEmpty()) {
            return null;
        }
        Bounds bounds = Bounds.centeredOn((int) Math.floor(center.x()), (int) Math.floor(center.z()), terreno.getSize());
        return new Claim(
                terreno.getId(),
                terreno.getDonoUUID(),
                center.worldName(),
                bounds,
                Boolean.TRUE.equals(terreno.getPvp()),
                Boolean.TRUE.equals(terreno.getMobs()),
                Boolean.TRUE.equals(terreno.getPublicAccess()),
                membersOf(terreno));
    }

    private static Map<String, TerrenoRole> membersOf(Terreno terreno) {
        Map<String, TerrenoRole> members = new HashMap<String, TerrenoRole>();
        for (TerrenoMember member : terreno.getMembers()) {
            if (member.getMemberUUID() != null && member.getMemberRole() != null) {
                members.put(member.getMemberUUID(), member.getMemberRole());
            }
        }
        return members;
    }
}
