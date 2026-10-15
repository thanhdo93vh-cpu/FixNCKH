package com.example.hauiTrash.entity.neo4j;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;

@Node("Source")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceNode {

    @Id
    @GeneratedValue
    private Long id;

    private String code;

    private String name;

    private String type;

    private String organization;

    private String reference;

    private String publicationDate;

    private String version;

    private String url;

    private String description;
}