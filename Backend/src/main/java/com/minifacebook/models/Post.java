
package com.minifacebook.models;

import java.util.LinkedHashSet;
import java.util.Set;

public class Post {

    private String postId;
    private String authorId;
    private String content;
    private String createdAt;

    private Set<String> likes = new LinkedHashSet<>();

    public Post() {
    }

    public Post(String postId, String authorId,
                String content, String createdAt) {

        this.postId = postId;
        this.authorId = authorId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public Set<String> getLikes() {
        if (likes == null) {
            likes = new LinkedHashSet<>();
        }
        return likes;
    }
    
    public void setLikes(Set<String> likes) {
        this.likes = likes == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(likes);
    }
}
