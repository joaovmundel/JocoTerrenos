package io.github.joaovmundel.jocoTerrenos.protection;

import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.models.TerrenoMember;
import io.github.joaovmundel.jocoTerrenos.models.TerrenoRole;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClaimMapperTest {

    private static Terreno terreno(String location, int size) {
        Terreno terreno = new Terreno();
        terreno.setId(7L);
        terreno.setName("casa");
        terreno.setDonoUUID("owner-uuid");
        terreno.setLocation(location);
        terreno.setSize(size);
        terreno.setPvp(false);
        terreno.setMobs(true);
        terreno.setPublicAccess(false);
        return terreno;
    }

    @Test
    public void mapsLocationAndSizeToBounds() {
        Claim claim = ClaimMapper.toClaim(terreno("world:100.50:64.00:200.50", 5));

        assertEquals(7L, claim.getId());
        assertEquals("world", claim.getWorld());
        assertEquals(new Bounds(98, 102, 198, 202), claim.getBounds());
        assertEquals("owner-uuid", claim.getOwnerKey());
    }

    @Test
    public void negativeCoordinatesFloorLikeBlockCoordinates() {
        // block (-4, -1): -3.50 and -0.50 are both inside blocks -4 and -1
        Claim claim = ClaimMapper.toClaim(terreno("world:-3.50:70.00:-0.50", 5));

        assertEquals(new Bounds(-6, -2, -3, 1), claim.getBounds());
    }

    @Test
    public void commaDecimalSeparatorIsAccepted() {
        Claim claim = ClaimMapper.toClaim(terreno("world:100,50:64,00:200,50", 5));

        assertEquals(new Bounds(98, 102, 198, 202), claim.getBounds());
    }

    @Test
    public void copiesFlagsAndMembers() {
        Terreno terreno = terreno("world:0.50:64.00:0.50", 5);
        terreno.setPvp(true);
        terreno.setMobs(false);
        terreno.setPublicAccess(true);
        terreno.getMembers().add(new TerrenoMember(7L, "friend-uuid", TerrenoRole.MEMBER));

        Claim claim = ClaimMapper.toClaim(terreno);

        assertTrue(claim.isPvp());
        assertFalse(claim.isMobs());
        assertTrue(claim.isPublicAccess());
        assertTrue(claim.allowsBuild("friend-uuid"));
        assertFalse(claim.allowsBuild("someone-else"));
    }

    @Test
    public void missingFlagsDefaultToFalse() {
        Terreno terreno = terreno("world:0.50:64.00:0.50", 5);
        terreno.setPvp(null);
        terreno.setMobs(null);
        terreno.setPublicAccess(null);

        Claim claim = ClaimMapper.toClaim(terreno);

        assertFalse(claim.isPvp());
        assertFalse(claim.isMobs());
        assertFalse(claim.isPublicAccess());
    }

    @Test
    public void unreadableLocationYieldsNull() {
        assertNull(ClaimMapper.toClaim(terreno("garbage", 5)));
        assertNull(ClaimMapper.toClaim(terreno("", 5)));
        assertNull(ClaimMapper.toClaim(terreno(null, 5)));
    }

    @Test
    public void invalidSizeYieldsNull() {
        assertNull(ClaimMapper.toClaim(terreno("world:0.50:64.00:0.50", 0)));
        assertNull(ClaimMapper.toClaim(terreno("world:0.50:64.00:0.50", -3)));
    }
}
