package com.example.hauiTrash.entity.neo4j;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("TrashBin")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrashBinNode {

    @Id
    private Long id;

    private String name;

    private String code;

    private String description;

    private Boolean isActive;

    private String color;

    private String colorCode;
}