package com.example.hauiTrash.entity.neo4j;

import lombok.*;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("TrashItemAlias")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrashItemAliasNode {

    @Id
    @GeneratedValue
    private Long id;

    private String alias;

    private String aliasLabel;

    private String aliasDisplay;

    private String aliasSource;

    private Boolean isActive;
}