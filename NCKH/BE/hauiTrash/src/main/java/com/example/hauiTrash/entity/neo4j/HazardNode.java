package com.example.hauiTrash.entity.neo4j;

import lombok.*;
import org.springframework.data.neo4j.core.schema.*;

@Node("Hazard")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HazardNode {

    @Id
    @GeneratedValue
    private Long id;

    private String code;

    private String name;

    private String description;
}