package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trash_bin")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TrashBin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_trash", nullable = false, length = 100)
    private String nameTrash;  // GIẤY, NHỰA, KIM LOẠI, THỦY TINH

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;  // giay, nhua, kim_loai, thuy_tinh

    @Column(name = "color_name", length = 50)
    private String colorName;  // Xanh dương, Xanh lá, Vàng, Xanh ngọc

    @Column(name = "color_code", length = 20)
    private String colorCode;  // #3B82F6, #22C55E, #EAB308, #14B8A6

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "sort_order")
    private Integer sortOrder;  // Thứ tự hiển thị: 1,2,3,4

    // Quan hệ với TrashItem
    @OneToMany(mappedBy = "trashBin", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TrashItem> trashItems = new ArrayList<>();

    // Helper methods
    public void addTrashItem(TrashItem item) {
        trashItems.add(item);
        item.setTrashBin(this);
    }

    public void removeTrashItem(TrashItem item) {
        trashItems.remove(item);
        item.setTrashBin(null);
    }

    @PrePersist
    protected void onCreate() {
        if (isActive == null) {
            isActive = true;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }
}