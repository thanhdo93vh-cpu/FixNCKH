package com.example.hauiTrash.entity.neo4j;

import lombok.*;
import org.springframework.data.neo4j.core.schema.*;

import java.util.List;

@Node("HuongXuLy")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HuongXuLyNode {

    @Id
    private Long id;

    private String ten;

    private String moTa;

    private String luuY;

    private List<String> cacBuoc;
}