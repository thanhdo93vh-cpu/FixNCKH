package com.example.hauiTrash.entity.neo4j;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

@Node("TrashType")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrashTypeNode {

    @Id
    private Long id;

    private String name;

    private String code;

    private String description;

    private Boolean isActive;

    @Relationship(
            type = "USES_BIN",
            direction = Relationship.Direction.OUTGOING
    )
    private TrashBinNode trashBin;
}