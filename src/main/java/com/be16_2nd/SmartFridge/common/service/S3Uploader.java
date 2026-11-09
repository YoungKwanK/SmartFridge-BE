package com.be16_2nd.SmartFridge.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3Uploader {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String upload(MultipartFile file) {
        // 원본 파일명을 키로 쓰면 이름이 같은 파일끼리 덮어쓰고, 한쪽을 지울 때 다른 쪽 파일까지 사라진다
        String key = createKey(file.getOriginalFilename());

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes()));

            return s3Client.utilities().getUrl(builder -> builder.bucket(bucket).key(key)).toExternalForm();

        } catch (IOException e) {
            throw new IllegalArgumentException("S3 업로드 실패", e);
        }
    }

    static String createKey(String originalFilename) {
        String extension = "";
        if (originalFilename != null) {
            int dotIndex = originalFilename.lastIndexOf('.');
            String candidate = dotIndex >= 0 ? originalFilename.substring(dotIndex) : "";
            if (candidate.matches("\\.[A-Za-z0-9]{1,10}")) {
                extension = candidate;
            }
        }
        return UUID.randomUUID() + extension;
    }

    // 삭제 메서드
    public void delete(String fileUrl) {
        try {
            String key = extractKeyFromUrl(fileUrl);

            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);

        } catch (Exception e) {
            throw new IllegalArgumentException("S3 삭제 실패", e);
        }
    }

    // URL에서 key 추출하는 유틸 함수
    private String extractKeyFromUrl(String fileUrl) {
        int index = fileUrl.indexOf(".amazonaws.com/");
        if (index == -1) {
            throw new IllegalArgumentException("잘못된 S3 URL 형식입니다.");
        }
        return fileUrl.substring(index + ".amazonaws.com/".length());
    }
}