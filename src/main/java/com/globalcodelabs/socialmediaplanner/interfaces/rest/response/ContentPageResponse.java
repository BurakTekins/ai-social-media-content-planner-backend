package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import org.springframework.data.domain.Page;

import java.util.List;

public record ContentPageResponse(
        List<ContentResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static ContentPageResponse from(Page<Content> contents) {
        return new ContentPageResponse(
                contents.getContent().stream().map(ContentResponse::from).toList(),
                contents.getNumber(),
                contents.getSize(),
                contents.getTotalElements(),
                contents.getTotalPages()
        );
    }
}
