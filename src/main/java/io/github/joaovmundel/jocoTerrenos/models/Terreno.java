package io.github.joaovmundel.jocoTerrenos.models;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Terreno {
    private Long id;
    private String donoUUID;
    private String location;
    private Integer size;
    private Boolean pvp;
    private Boolean mobs;
    private Boolean publicAccess;
    private List<TerrenoMember> members = new ArrayList<>();
}
