package com.example.hauiTrash.entity.neo4j;

import lombok.*;
import org.springframework.data.neo4j.core.schema.*;

import java.util.ArrayList;
import java.util.List;

@Node("Treatment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TreatmentNode {

    @Id
    @GeneratedValue
    private Long id;

    private String code;

    private String name;

    private String description;

    @Relationship(
            type = "HAS_GUIDANCE",
            direction = Relationship.Direction.OUTGOING
    )
    @Builder.Default
    private List<HuongXuLyNode> guidance = new ArrayList<>();
}