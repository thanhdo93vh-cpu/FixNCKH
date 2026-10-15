package com.example.hauiTrash.entity.neo4j;

import lombok.*;
import org.springframework.data.neo4j.core.schema.*;

import java.util.ArrayList;
import java.util.List;

@Node("VatLieu")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VatLieuNode {

    @Id
    @GeneratedValue
    private Long id;

    private String code;

    private String name;

    private String description;

    @Relationship(
            type = "APPLIES_RULE",
            direction = Relationship.Direction.OUTGOING
    )
    @Builder.Default
    private List<RuleNode> rules = new ArrayList<>();
}