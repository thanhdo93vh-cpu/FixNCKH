package com.example.hauiTrash.entity.neo4j;

import lombok.*;
import org.springframework.data.neo4j.core.schema.*;

import java.util.ArrayList;
import java.util.List;

@Node("Rule")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleNode {

    @Id
    @GeneratedValue
    private Long id;

    private String code;

    private String name;

    private String description;

    @Relationship(
            type = "INFERS_TYPE",
            direction = Relationship.Direction.OUTGOING
    )
    private TrashTypeNode trashType;

    @Relationship(
            type = "REQUIRES_TREATMENT",
            direction = Relationship.Direction.OUTGOING
    )
    private TreatmentNode treatment;

    @Relationship(
            type = "SUPPORTED_BY",
            direction = Relationship.Direction.OUTGOING
    )
    @Builder.Default
    private List<SourceNode> sources = new ArrayList<>();
}