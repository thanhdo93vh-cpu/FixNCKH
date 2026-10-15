package com.example.hauiTrash.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "blog_posts")
public class BlogPost {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Account author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private BlogCategory category;

    @Column(name = "title", length = 255)
    private String title;

    @Lob
    @Column(name = "summary")
    private String summary;

    @Lob
    @Column(name = "content_html")
    private String contentHtml;

    @Lob
    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "status", length = 20)
    private String status; // DRAFT|PUBLISHED|HIDDEN

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
