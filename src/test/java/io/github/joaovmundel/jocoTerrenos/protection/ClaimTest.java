package io.github.joaovmundel.jocoTerrenos.protection;

import io.github.joaovmundel.jocoTerrenos.models.TerrenoRole;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClaimTest {

    private static final String OWNER = "owner-uuid";
    private static final String MEMBER = "member-uuid";
    private static final String STRANGER = "stranger-uuid";

    private static Claim claim(boolean publicAccess, Map<String, TerrenoRole> members) {
        return new Claim(1L, OWNER, "world", new Bounds(0, 10, 0, 10), false, true, publicAccess, members);
    }

    @Test
    public void ownerCanBuildAndUse() {
        Claim claim = claim(false, Collections.<String, TerrenoRole>emptyMap());

        assertTrue(claim.allowsBuild(OWNER));
        assertTrue(claim.allowsUse(OWNER));
    }

    @Test
    public void strangerCanDoNothingOnPrivateLand() {
        Claim claim = claim(false, Collections.<String, TerrenoRole>emptyMap());

        assertFalse(claim.allowsBuild(STRANGER));
        assertFalse(claim.allowsUse(STRANGER));
    }

    @Test
    public void publicLandLetsVisitorsUseButNotBuild() {
        Claim claim = claim(true, Collections.<String, TerrenoRole>emptyMap());

        assertFalse(claim.allowsBuild(STRANGER));
        assertTrue(claim.allowsUse(STRANGER));
    }

    @Test
    public void membersCanBuildWhateverTheirRole() {
        Map<String, TerrenoRole> members = new HashMap<String, TerrenoRole>();
        members.put(MEMBER, TerrenoRole.MEMBER);
        members.put("admin-uuid", TerrenoRole.ADMIN);
        Claim claim = claim(false, members);

        assertTrue(claim.allowsBuild(MEMBER));
        assertTrue(claim.allowsBuild("admin-uuid"));
        assertFalse(claim.allowsBuild(STRANGER));
    }

    @Test
    public void membersAreCopiedDefensively() {
        Map<String, TerrenoRole> members = new HashMap<String, TerrenoRole>();
        Claim claim = claim(false, members);

        members.put(STRANGER, TerrenoRole.MEMBER);

        assertFalse(claim.allowsBuild(STRANGER));
    }

    @Test(expected = IllegalArgumentException.class)
    public void ownerIsRequired() {
        new Claim(1L, null, "world", new Bounds(0, 1, 0, 1), false, true, false, Collections.<String, TerrenoRole>emptyMap());
    }
}
