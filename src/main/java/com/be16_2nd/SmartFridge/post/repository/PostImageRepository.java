package com.be16_2nd.SmartFridge.post.repository;

import com.be16_2nd.SmartFridge.post.domain.Post;
import com.be16_2nd.SmartFridge.post.domain.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    @Transactional
    @Modifying
    void deleteByPostAndImageUrl(Post post, String imageUrl);

    // 글마다 첫 번째로 첨부한 이미지 (게시판 목록 대표 이미지). 한 페이지의 글을 쿼리 한 번으로 조회한다
    @Query("select pi.post.id as postId, pi.imageUrl as imageUrl from PostImage pi " +
            "where pi.id in (select min(pi2.id) from PostImage pi2 where pi2.post.id in :postIds group by pi2.post.id)")
    List<PostThumbnail> findThumbnailsByPostIds(@Param("postIds") Collection<Long> postIds);

    interface PostThumbnail {
        Long getPostId();
        String getImageUrl();
    }
}
