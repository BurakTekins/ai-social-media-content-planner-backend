package com.globalcodelabs.socialmediaplanner.infrastructure.publishing.provider;

public final class LinkedInApiPaths {

    public static final String POSTS = "rest/posts";
    public static final String IMAGES = "rest/images";
    public static final String VIDEOS = "rest/videos";
    public static final String INITIALIZE_IMAGE_UPLOAD = IMAGES + "?action=initializeUpload";
    public static final String INITIALIZE_VIDEO_UPLOAD = VIDEOS + "?action=initializeUpload";
    public static final String FINALIZE_VIDEO_UPLOAD = VIDEOS + "?action=finalizeUpload";

    private LinkedInApiPaths() {
    }

    public static String imageStatus(String encodedImageUrn) {
        return IMAGES + "/" + encodedImageUrn;
    }

    public static String videoStatus(String encodedVideoUrn) {
        return VIDEOS + "/" + encodedVideoUrn;
    }
}
