package io.github.joaovmundel.jocoTerrenos.protection;

import io.github.joaovmundel.jocoTerrenos.models.TerrenoRole;
import io.github.joaovmundel.jocoTerrenos.protection.FlightRules.Verdict;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class FlightRulesTest {

    private static Claim land(boolean publicAccess, boolean forSale) {
        Map<String, TerrenoRole> members = new HashMap<String, TerrenoRole>();
        members.put("member", TerrenoRole.MEMBER);
        return new Claim(1L, "owner", "world", new Bounds(0, 10, 0, 10), false, true, publicAccess, forSale, members);
    }

    @Test
    public void theOwnerWithPermissionFliesInTheirLand() {
        assertEquals(Verdict.ALLOWED, FlightRules.check(true, true, land(false, false), "owner"));
    }

    @Test
    public void aMemberWithPermissionFliesToo() {
        assertEquals(Verdict.ALLOWED, FlightRules.check(true, true, land(false, false), "member"));
    }

    @Test
    public void aStrangerWithPermissionCannotFlyEvenOnAPublicLand() {
        assertEquals(Verdict.NOT_IN_OWN_LAND, FlightRules.check(true, true, land(true, false), "stranger"));
    }

    @Test
    public void aStrangerCannotFlyOnALandThatIsForSaleEither() {
        assertEquals(Verdict.NOT_IN_OWN_LAND, FlightRules.check(true, true, land(true, true), "stranger"));
    }

    @Test
    public void unclaimedGroundIsNeverAllowed() {
        assertEquals(Verdict.NOT_IN_OWN_LAND, FlightRules.check(true, true, null, "owner"));
    }

    @Test
    public void withoutThePermissionNobodyFliesNotEvenTheOwner() {
        assertEquals(Verdict.NO_PERMISSION, FlightRules.check(false, true, land(false, false), "owner"));
    }

    @Test
    public void theWorldMustBeOneWhereLandsCanBeBought() {
        assertEquals(Verdict.WORLD_NOT_ALLOWED, FlightRules.check(true, false, land(false, false), "owner"));
    }

    @Test
    public void thePermissionIsReportedBeforeTheWorldAndTheWorldBeforeTheLand() {
        assertEquals(Verdict.NO_PERMISSION, FlightRules.check(false, false, null, "owner"));
        assertEquals(Verdict.WORLD_NOT_ALLOWED, FlightRules.check(true, false, null, "owner"));
    }

    @Test
    public void aClaimWithNoMembersOnlyLetsTheOwnerFly() {
        Claim alone = new Claim(2L, "owner", "world", new Bounds(0, 5, 0, 5), false, true, true, false,
                Collections.<String, TerrenoRole>emptyMap());

        assertEquals(Verdict.ALLOWED, FlightRules.check(true, true, alone, "owner"));
        assertEquals(Verdict.NOT_IN_OWN_LAND, FlightRules.check(true, true, alone, "member"));
    }
}
