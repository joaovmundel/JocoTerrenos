package io.github.joaovmundel.jocoTerrenos.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerrenoMember {
    private Long terrenoId;
    private String memberUUID;
    private TerrenoRole memberRole;
}
