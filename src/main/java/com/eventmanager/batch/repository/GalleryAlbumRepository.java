package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.GalleryAlbum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GalleryAlbumRepository extends JpaRepository<GalleryAlbum, Long> {

    List<GalleryAlbum> findByTenantIdAndIsPublicTrueOrderByDisplayOrderAsc(String tenantId);
}
