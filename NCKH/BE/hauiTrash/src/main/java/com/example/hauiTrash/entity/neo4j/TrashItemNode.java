package com.example.hauiTrash.entity.neo4j;

import lombok.*;
import org.springframework.data.neo4j.core.schema.*;

import java.util.ArrayList;
import java.util.List;

@Node("TrashItem")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrashItemNode {

    @Id
    private Long id;

    private String label;

    private String labelDisplay;

    private Boolean isActive;

    private String material;

    private String note;

    private String action;

    @Relationship(
            type = "HAS_ALIAS",
            direction = Relationship.Direction.OUTGOING
    )
    @Builder.Default
    private List<TrashItemAliasNode> aliases = new ArrayList<>();

    /*
     * Quan hệ cũ - giữ lại để tương thích backend hiện tại.
     */
    @Relationship(
            type = "BELONGS_TO",
            direction = Relationship.Direction.OUTGOING
    )
    private TrashTypeNode trashType;

    /*
     * KG mới:
     * TrashItem -> VatLieu
     */
    @Relationship(
            type = "MADE_OF",
            direction = Relationship.Direction.OUTGOING
    )
    @Builder.Default
    private List<VatLieuNode> materials = new ArrayList<>();

    /*
     * Rule áp dụng trực tiếp cho đối tượng.
     */
    @Relationship(
            type = "APPLIES_RULE",
            direction = Relationship.Direction.OUTGOING
    )
    @Builder.Default
    private List<RuleNode> rules = new ArrayList<>();

    /*
     * Nguy cơ của đối tượng.
     */
    @Relationship(
            type = "HAS_HAZARD",
            direction = Relationship.Direction.OUTGOING
    )
    @Builder.Default
    private List<HazardNode> hazards = new ArrayList<>();

    /*
     * Quan hệ cũ đang tồn tại trong KG.
     */
    @Relationship(
            type = "BO_VAO",
            direction = Relationship.Direction.OUTGOING
    )
    private TrashBinNode trashBin;

    /*
     * Quan hệ cũ:
     * TrashItem -> HuongXuLy
     */
    @Relationship(
            type = "CO_HUONG_XU_LY",
            direction = Relationship.Direction.OUTGOING
    )
    @Builder.Default
    private List<HuongXuLyNode> huongXuLy = new ArrayList<>();
}