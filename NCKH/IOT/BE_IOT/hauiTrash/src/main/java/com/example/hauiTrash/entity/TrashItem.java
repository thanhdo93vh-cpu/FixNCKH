package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;


import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "trash_items")
public class TrashItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 120)
    private String label;

    @Column(name = "label_display", length = 180)
    private String labelDisplay;

    @Column(nullable = false, length = 30)
    private String status; // ACTIVE / NEED_REVIEW / DISABLED


    /*
     Visual RAG: alias label
     ví dụ:
     coke_can
     soda_can
     metal_can
     -> map về "can"
     */
    @OneToMany(mappedBy = "trashItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TrashItemAlias> aliases= new ArrayList<>();

    /*
     mapping sang loại rác
     */
    @OneToMany(mappedBy = "trashItem", cascade = CascadeType.ALL)
    @Builder.Default
    private List<TrashItemMapping> mappings = new ArrayList<>();

    /*
     knowledge của rác
     */
    @OneToMany(mappedBy = "trashItem", cascade = CascadeType.ALL)
    @Builder.Default
    private List<TrashItemKnowledge> knowledge= new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = "ACTIVE";
        }
    }

}