package com.example.hauiTrash.repository;


import com.example.hauiTrash.entity.TrashItemKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrashItemKnowledgeRepository extends JpaRepository<TrashItemKnowledge, Integer> {
    @Query("select k from TrashItemKnowledge k where k.trashItem.id = :itemId and k.isActive = true")
    Optional<TrashItemKnowledge> findActiveByTrashItemId(@Param("itemId") Integer itemId);
    @Query("""
        select k from TrashItemKnowledge k
        where k.isActive = true and k.trashItem.id in :ids
    """)
    List<TrashItemKnowledge> findActiveByTrashItemIdIn(@Param("ids") List<Integer> ids);

    @Query("select max(k.version) from TrashItemKnowledge k where k.trashItem.id = :itemId")
    Integer maxVersion(@Param("itemId") Integer itemId);


    @Modifying
    @Query("update TrashItemKnowledge k set k.isActive=false where k.trashItem.id=:itemId and k.isActive=true")
    int deactivateAll(@Param("itemId") Integer itemId);

    /**
     * RAG: Load all active knowledge with embeddings for similarity search.
     */
    @Query("select k from TrashItemKnowledge k join fetch k.trashItem where k.isActive = true and k.embeddingJson is not null")
    List<TrashItemKnowledge> findAllActiveWithEmbeddings();

    /**
     * RAG: Load all active knowledge (for embedding rebuild).
     */
    @Query("select k from TrashItemKnowledge k join fetch k.trashItem where k.isActive = true")
    List<TrashItemKnowledge> findAllActive();
}