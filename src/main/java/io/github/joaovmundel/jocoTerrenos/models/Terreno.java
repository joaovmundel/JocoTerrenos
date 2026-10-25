package io.github.joaovmundel.jocoTerrenos.models;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A bought land. On Minecraft 1.5.2 players have no UUID, so the owner is the lowercase player name
 * (see {@link io.github.joaovmundel.jocoTerrenos.utils.PlayerKeys}).
 */
@Data
public class Terreno {
    private Long id;
    private String name;
    private String donoNome;
    private String location;
    private Integer size;
    private Boolean pvp;
    private Boolean mobs;
    private Boolean publicAccess;
    /** Asking price in the server currency, or {@code null} when the land is not for sale. */
    private BigDecimal salePrice;
    private List<TerrenoMember> members = new ArrayList<TerrenoMember>();
}
