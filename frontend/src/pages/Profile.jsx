
import { useEffect, useState } from "react";

import {
    UserRound,
    CalendarDays,
    Users,
    UserPlus,
    RefreshCw,
    Pencil,
    X,
    Heart,
    MessageCircle,
    Clock3,
    Trash2,
    Check,
} from "lucide-react";

import { sendRequest } from "../services/api";
import { useAuth } from "../context/AuthContext";

export default function Profile() {
    const { user, sessionToken } = useAuth();

    const [profile, setProfile] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    // Posts
    const [posts, setPosts] = useState([]);
    const [postsLoading, setPostsLoading] = useState(true);
    const [postsError, setPostsError] = useState("");

    // Like functionality
    const [likingPosts, setLikingPosts] = useState({});

    // Comment functionality
    const [comments, setComments] = useState({});
    const [commentInputs, setCommentInputs] = useState({});
    const [expandedComments, setExpandedComments] = useState({});
    const [commentLoading, setCommentLoading] = useState({});
    const [submittingComments, setSubmittingComments] = useState({});

    // Edit profile states
    const [editing, setEditing] = useState(false);
    const [bioDraft, setBioDraft] = useState("");
    const [saving, setSaving] = useState(false);
    const [saveError, setSaveError] = useState("");
    const [saveSuccess, setSaveSuccess] = useState("");

    // Edit/delete post states
    const [editingPostId, setEditingPostId] = useState(null);
    const [postDraft, setPostDraft] = useState("");
    const [savingPost, setSavingPost] = useState(false);
    const [deletingPosts, setDeletingPosts] = useState({});

    // Edit/delete comment states
    const [editingCommentId, setEditingCommentId] = useState(null);
    const [commentDraft, setCommentDraft] = useState("");
    const [savingComment, setSavingComment] = useState(false);
    const [deletingComments, setDeletingComments] = useState({});

    // Load profile
    async function loadProfile() {
        try {
            setLoading(true);
            setError("");

            const result = await sendRequest("GET_MY_PROFILE", {
                sessionToken,
            });

            if (result.status !== "SUCCESS") {
                throw new Error(
                    result.message || "Unable to load profile"
                );
            }

            setProfile(result.user);
        } catch (err) {
            console.error("Profile loading error:", err);
            setError(err.message || "Unable to load profile");
        } finally {
            setLoading(false);
        }
    }

    // Load my posts
    async function loadMyPosts() {
        try {
            setPostsLoading(true);
            setPostsError("");

            const result = await sendRequest("GET_MY_POSTS", {
                sessionToken,
            });

            if (result.status !== "SUCCESS") {
                throw new Error(
                    result.message || "Unable to load your posts"
                );
            }

            setPosts(result.posts || []);
        } catch (err) {
            console.error("My posts loading error:", err);
            setPostsError(err.message || "Unable to load posts");
        } finally {
            setPostsLoading(false);
        }
    }

    useEffect(() => {
        if (sessionToken) {
            loadProfile();
            loadMyPosts();
        }
    }, [sessionToken]);

    // Like / unlike post
    async function toggleLike(post) {
        const postId = post.postId;

        if (likingPosts[postId]) return;

        const wasLiked = post.isLiked;

        setLikingPosts((prev) => ({
            ...prev,
            [postId]: true,
        }));

        setPosts((prev) =>
            prev.map((item) =>
                item.postId === postId
                    ? {
                        ...item,
                        isLiked: !wasLiked,
                        likesCount: Math.max(
                            0,
                            (item.likesCount || 0) +
                            (wasLiked ? -1 : 1)
                        ),
                    }
                    : item
            )
        );

        try {
            const result = await sendRequest(
                wasLiked ? "UNLIKE_POST" : "LIKE_POST",
                {
                    sessionToken,
                    postId,
                }
            );

            if (result.status !== "SUCCESS") {
                throw new Error(
                    result.message || "Unable to update like"
                );
            }
        } catch (err) {
            setPosts((prev) =>
                prev.map((item) =>
                    item.postId === postId
                        ? {
                            ...item,
                            isLiked: wasLiked,
                            likesCount: Math.max(
                                0,
                                (item.likesCount || 0) +
                                (wasLiked ? 1 : -1)
                            ),
                        }
                        : item
                )
            );

            console.error("Like error:", err);
            alert(err.message || "Unable to update like");
        } finally {
            setLikingPosts((prev) => ({
                ...prev,
                [postId]: false,
            }));
        }
    }

    // Load comments
    async function loadComments(postId) {
        try {
            setCommentLoading((prev) => ({
                ...prev,
                [postId]: true,
            }));

            const result = await sendRequest("GET_COMMENTS", {
                sessionToken,
                postId,
            });

            if (result.status !== "SUCCESS") {
                throw new Error(
                    result.message || "Unable to load comments"
                );
            }

            setComments((prev) => ({
                ...prev,
                [postId]: result.comments || [],
            }));
        } catch (err) {
            console.error("Comments loading error:", err);
        } finally {
            setCommentLoading((prev) => ({
                ...prev,
                [postId]: false,
            }));
        }
    }

    // Expand / collapse comments
    async function toggleComments(postId) {
        const isExpanded = expandedComments[postId];

        setExpandedComments((prev) => ({
            ...prev,
            [postId]: !isExpanded,
        }));

        if (!isExpanded) {
            await loadComments(postId);
        }
    }

    // Add comment
    async function addComment(postId, e) {
        e.preventDefault();

        const content = commentInputs[postId]?.trim();

        if (!content || submittingComments[postId]) return;

        try {
            setSubmittingComments((prev) => ({
                ...prev,
                [postId]: true,
            }));

            const result = await sendRequest("ADD_COMMENT", {
                sessionToken,
                postId,
                content,
            });

            if (result.status !== "SUCCESS") {
                throw new Error(
                    result.message || "Unable to add comment"
                );
            }

            setCommentInputs((prev) => ({
                ...prev,
                [postId]: "",
            }));

            await loadComments(postId);

            setPosts((prev) =>
                prev.map((post) =>
                    post.postId === postId
                        ? {
                            ...post,
                            commentsCount:
                                (post.commentsCount || 0) + 1,
                        }
                        : post
                )
            );
        } catch (err) {
            console.error("Add comment error:", err);
            alert(err.message || "Unable to add comment");
        } finally {
            setSubmittingComments((prev) => ({
                ...prev,
                [postId]: false,
            }));
        }
    }

    // Start editing a post
    function startEditPost(post) {
        setEditingPostId(post.postId);
        setPostDraft(post.content);
    }

    // Cancel post editing
    function cancelEditPost() {
        if (savingPost) return;

        setEditingPostId(null);
        setPostDraft("");
    }

    // Update post
    async function handleUpdatePost(postId) {
        const content = postDraft.trim();

        if (!content) {
            alert("Post content cannot be empty");
            return;
        }

        if (content.length > 2000) {
            alert("Post cannot exceed 2000 characters");
            return;
        }

        try {
            setSavingPost(true);

            const result = await sendRequest("UPDATE_POST", {
                sessionToken,
                postId,
                content,
            });

            if (result.status !== "SUCCESS") {
                throw new Error(
                    result.message || "Unable to update post"
                );
            }

            setPosts((prev) =>
                prev.map((post) =>
                    post.postId === postId
                        ? {
                            ...post,
                            content: result.post?.content ?? content,
                        }
                        : post
                )
            );

            setEditingPostId(null);
            setPostDraft("");

        } catch (err) {
            console.error("Update post error:", err);
            alert(err.message || "Unable to update post");
        } finally {
            setSavingPost(false);
        }
    }

    // Delete post
    async function handleDeletePost(postId) {
        const confirmed = window.confirm(
            "Are you sure you want to delete this post? All comments on this post will also be deleted."
        );

        if (!confirmed || deletingPosts[postId]) return;

        try {
            setDeletingPosts((prev) => ({
                ...prev,
                [postId]: true,
            }));

            const result = await sendRequest("DELETE_POST", {
                sessionToken,
                postId,
            });

            if (result.status !== "SUCCESS") {
                throw new Error(
                    result.message || "Unable to delete post"
                );
            }

            setPosts((prev) =>
                prev.filter((post) => post.postId !== postId)
            );

            setComments((prev) => {
                const updated = { ...prev };
                delete updated[postId];
                return updated;
            });

            setExpandedComments((prev) => {
                const updated = { ...prev };
                delete updated[postId];
                return updated;
            });

            if (editingPostId === postId) {
                setEditingPostId(null);
                setPostDraft("");
            }

        } catch (err) {
            console.error("Delete post error:", err);
            alert(err.message || "Unable to delete post");
        } finally {
            setDeletingPosts((prev) => ({
                ...prev,
                [postId]: false,
            }));
        }
    }

    // Start editing a comment
    function startEditComment(comment) {
        setEditingCommentId(comment.commentId);
        setCommentDraft(comment.content);
    }

    // Cancel comment editing
    function cancelEditComment() {
        if (savingComment) return;

        setEditingCommentId(null);
        setCommentDraft("");
    }

    // Update comment
    async function handleUpdateComment(postId, commentId) {
        const content = commentDraft.trim();

        if (!content) {
            alert("Comment cannot be empty");
            return;
        }

        if (content.length > 1000) {
            alert("Comment cannot exceed 1000 characters");
            return;
        }

        try {
            setSavingComment(true);

            const result = await sendRequest("UPDATE_COMMENT", {
                sessionToken,
                commentId,
                content,
            });

            if (result.status !== "SUCCESS") {
                throw new Error(
                    result.message || "Unable to update comment"
                );
            }

            setComments((prev) => ({
                ...prev,
                [postId]: (prev[postId] || []).map((comment) =>
                    comment.commentId === commentId
                        ? {
                            ...comment,
                            content:
                                result.comment?.content ?? content,
                        }
                        : comment
                ),
            }));

            setEditingCommentId(null);
            setCommentDraft("");

        } catch (err) {
            console.error("Update comment error:", err);
            alert(err.message || "Unable to update comment");
        } finally {
            setSavingComment(false);
        }
    }

    // Delete comment
    async function handleDeleteComment(postId, commentId) {
        const confirmed = window.confirm(
            "Are you sure you want to delete this comment?"
        );

        if (!confirmed || deletingComments[commentId]) return;

        try {
            setDeletingComments((prev) => ({
                ...prev,
                [commentId]: true,
            }));

            const result = await sendRequest("DELETE_COMMENT", {
                sessionToken,
                commentId,
            });

            if (result.status !== "SUCCESS") {
                throw new Error(
                    result.message || "Unable to delete comment"
                );
            }

            setComments((prev) => ({
                ...prev,
                [postId]: (prev[postId] || []).filter(
                    (comment) => comment.commentId !== commentId
                ),
            }));

            setPosts((prev) =>
                prev.map((post) =>
                    post.postId === postId
                        ? {
                            ...post,
                            commentsCount: Math.max(
                                0,
                                (post.commentsCount || 0) - 1
                            ),
                        }
                        : post
                )
            );

            if (editingCommentId === commentId) {
                setEditingCommentId(null);
                setCommentDraft("");
            }

        } catch (err) {
            console.error("Delete comment error:", err);
            alert(err.message || "Unable to delete comment");
        } finally {
            setDeletingComments((prev) => ({
                ...prev,
                [commentId]: false,
            }));
        }
    }

    // Open edit profile dialog
    function openEditDialog() {
        setBioDraft(profile?.bio || "");
        setSaveError("");
        setSaveSuccess("");
        setEditing(true);
    }

    function closeEditDialog() {
        if (saving) return;

        setEditing(false);
        setSaveError("");
    }

    // Save profile
    async function handleSaveProfile(e) {
        e.preventDefault();

        try {
            setSaving(true);
            setSaveError("");
            setSaveSuccess("");

            const result = await sendRequest("UPDATE_PROFILE", {
                sessionToken,
                bio: bioDraft,
            });

            if (result.status !== "SUCCESS") {
                throw new Error(
                    result.message || "Unable to update profile"
                );
            }

            setProfile(result.user);
            setEditing(false);
            setSaveSuccess("Profile updated successfully!");

        } catch (err) {
            console.error("Profile update error:", err);
            setSaveError(err.message || "Something went wrong");
        } finally {
            setSaving(false);
        }
    }

    // Loading state
    if (loading) {
        return (
            <main className="profile-page">
                <div className="profile-status">
                    <RefreshCw
                        className="refresh-spinning"
                        size={22}
                    />
                    <p>Loading your profile...</p>
                </div>
            </main>
        );
    }

    // Error state
    if (error) {
        return (
            <main className="profile-page">
                <div className="profile-status">
                    <p className="error-message">{error}</p>

                    <button type="button" onClick={loadProfile}>
                        Try Again
                    </button>
                </div>
            </main>
        );
    }

    const username = profile?.username || user?.username || "User";
    const initial = username.charAt(0).toUpperCase();

    const formattedDate = profile?.createdAt
        ? new Date(profile.createdAt).toLocaleDateString("en-IN", {
            day: "numeric",
            month: "long",
            year: "numeric",
        })
        : "Not available";

    return (
        <main className="profile-page">
            <div className="profile-container">

                {/* Cover */}
                <section className="profile-cover">
                    <div className="cover-decoration cover-decoration-one" />
                    <div className="cover-decoration cover-decoration-two" />
                </section>

                {/* Profile header */}
                <section className="profile-header-card">
                    <div className="profile-main-info">
                        <div className="profile-avatar">
                            {initial}
                        </div>

                        <div className="profile-identity">
                            <h1>{username}</h1>
                            <p>MiniFacebook Member</p>
                        </div>
                    </div>

                    <div className="profile-header-actions">
                        <button
                            type="button"
                            className="edit-profile-button"
                            onClick={openEditDialog}
                        >
                            <Pencil size={16} />
                            Edit Profile
                        </button>
                    </div>

                    <div className="profile-tabs">
                        <button
                            type="button"
                            className="profile-tab active"
                        >
                            Overview
                        </button>
                    </div>
                </section>

                {/* Profile details */}
                <section className="profile-content">

                    {/* About */}
                    <div className="profile-info-card">
                        <div className="profile-card-heading">
                            <h2>About</h2>

                            <button
                                type="button"
                                className="profile-refresh-button"
                                onClick={loadProfile}
                                title="Refresh profile"
                            >
                                <RefreshCw size={17} />
                            </button>
                        </div>

                        <div className="profile-bio">
                            {profile?.bio?.trim()
                                ? profile.bio
                                : "No bio added yet."}
                        </div>

                        <div className="profile-info-row">
                            <div className="profile-info-icon">
                                <UserRound size={19} />
                            </div>

                            <div>
                                <strong>Username</strong>
                                <p>{username}</p>
                            </div>
                        </div>

                        <div className="profile-info-row">
                            <div className="profile-info-icon">
                                <CalendarDays size={19} />
                            </div>

                            <div>
                                <strong>Joined</strong>
                                <p>{formattedDate}</p>
                            </div>
                        </div>

                        {saveSuccess && (
                            <p className="profile-success-message">
                                {saveSuccess}
                            </p>
                        )}
                    </div>

                    {/* Connections */}
                    <div className="profile-stats-card">
                        <h2>Connections</h2>

                        <div className="profile-stats-grid">
                            <div className="profile-stat">
                                <div className="profile-stat-icon">
                                    <Users size={23} />
                                </div>

                                <strong>
                                    {profile?.followersCount ?? 0}
                                </strong>

                                <span>Followers</span>
                            </div>

                            <div className="profile-stat">
                                <div className="profile-stat-icon">
                                    <UserPlus size={23} />
                                </div>

                                <strong>
                                    {profile?.followingCount ?? 0}
                                </strong>

                                <span>Following</span>
                            </div>
                        </div>
                    </div>
                </section>

                {/* My Posts */}
                <section className="my-posts-section">

                    <div className="my-posts-heading">
                        <div>
                            <h2>My Posts</h2>
                            <p>Posts shared by you</p>
                        </div>

                        <div className="my-posts-heading-actions">
                            <span className="my-posts-count">
                                {posts.length}{" "}
                                {posts.length === 1 ? "Post" : "Posts"}
                            </span>

                            <button
                                type="button"
                                className="profile-refresh-button"
                                onClick={loadMyPosts}
                                title="Refresh posts"
                                disabled={postsLoading}
                            >
                                <RefreshCw size={17} />
                            </button>
                        </div>
                    </div>

                    {postsLoading ? (
                        <div className="posts-status">
                            <RefreshCw
                                size={20}
                                className="refresh-spinning"
                            />
                            <p>Loading your posts...</p>
                        </div>
                    ) : postsError ? (
                        <div className="posts-status">
                            <p className="error-message">
                                {postsError}
                            </p>

                            <button
                                type="button"
                                onClick={loadMyPosts}
                            >
                                Try Again
                            </button>
                        </div>
                    ) : posts.length === 0 ? (
                        <div className="empty-posts">
                            <div className="empty-posts-icon">
                                <Pencil size={28} />
                            </div>

                            <h3>No posts yet</h3>

                            <p>
                                When you share something on MiniFacebook,
                                your posts will appear here.
                            </p>
                        </div>
                    ) : (
                        <div className="my-posts-list">
                            {posts.map((post) => (
                                <article
                                    className="my-post-card"
                                    key={post.postId}
                                >
                                    {/* Post header */}
                                    <div className="my-post-header">
                                        <div className="my-post-avatar">
                                            {initial}
                                        </div>

                                        <div className="my-post-author">
                                            <strong>
                                                {post.authorUsername || username}
                                            </strong>

                                            <span>
                                                <Clock3 size={13} />

                                                {post.createdAt
                                                    ? new Date(
                                                        post.createdAt
                                                    ).toLocaleString("en-IN", {
                                                        dateStyle: "medium",
                                                        timeStyle: "short",
                                                    })
                                                    : "Date unavailable"}
                                            </span>
                                        </div>

                                        {/* Post edit/delete actions */}
                                        <div className="post-management-actions">
                                            <button
                                                type="button"
                                                className="post-edit-button"
                                                onClick={() => startEditPost(post)}
                                                disabled={
                                                    editingPostId === post.postId ||
                                                    savingPost ||
                                                    deletingPosts[post.postId]
                                                }
                                                title="Edit post"
                                            >
                                                <Pencil size={15} />
                                                Edit
                                            </button>

                                            <button
                                                type="button"
                                                className="post-delete-button"
                                                onClick={() =>
                                                    handleDeletePost(post.postId)
                                                }
                                                disabled={
                                                    deletingPosts[post.postId] ||
                                                    savingPost
                                                }
                                                title="Delete post"
                                            >
                                                <Trash2 size={15} />
                                                {deletingPosts[post.postId]
                                                    ? "Deleting..."
                                                    : "Delete"}
                                            </button>
                                        </div>
                                    </div>

                                    {/* Post content / editing */}
                                    {editingPostId === post.postId ? (
                                        <div className="post-edit-form">
                                            <textarea
                                                value={postDraft}
                                                onChange={(e) =>
                                                    setPostDraft(e.target.value)
                                                }
                                                maxLength={2000}
                                                rows={5}
                                                placeholder="Write your post..."
                                                disabled={savingPost}
                                            />

                                            <div className="post-edit-footer">
                                                <span>
                                                    {postDraft.length}/2000
                                                    characters
                                                </span>

                                                <div>
                                                    <button
                                                        type="button"
                                                        className="post-cancel-button"
                                                        onClick={cancelEditPost}
                                                        disabled={savingPost}
                                                    >
                                                        <X size={15} />
                                                        Cancel
                                                    </button>

                                                    <button
                                                        type="button"
                                                        className="post-save-button"
                                                        onClick={() =>
                                                            handleUpdatePost(post.postId)
                                                        }
                                                        disabled={
                                                            savingPost ||
                                                            !postDraft.trim()
                                                        }
                                                    >
                                                        <Check size={15} />
                                                        {savingPost
                                                            ? "Saving..."
                                                            : "Save"}
                                                    </button>
                                                </div>
                                            </div>
                                        </div>
                                    ) : (
                                        <div className="my-post-content">
                                            {post.content}
                                        </div>
                                    )}

                                    <div className="my-post-divider" />

                                    {/* Post likes and comments */}
                                    <div className="my-post-stats">
                                        <button
                                            type="button"
                                            className={`my-post-action ${post.isLiked ? "liked" : ""}`}
                                            onClick={() => toggleLike(post)}
                                            disabled={likingPosts[post.postId]}
                                        >
                                            <Heart
                                                size={18}
                                                fill={
                                                    post.isLiked
                                                        ? "currentColor"
                                                        : "none"
                                                }
                                            />
                                            <span>
                                                {post.likesCount ?? 0} Likes
                                            </span>
                                        </button>

                                        <button
                                            type="button"
                                            className="my-post-action"
                                            onClick={() =>
                                                toggleComments(post.postId)
                                            }
                                        >
                                            <MessageCircle size={18} />
                                            <span>
                                                {post.commentsCount ?? 0} Comments
                                            </span>
                                        </button>
                                    </div>

                                    {/* Comments section */}
                                    {expandedComments[post.postId] && (
                                        <div className="my-post-comments">
                                            <h4>Comments</h4>

                                            {commentLoading[post.postId] ? (
                                                <p className="comments-loading">
                                                    Loading comments...
                                                </p>
                                            ) : (
                                                <>
                                                    {(comments[post.postId] || [])
                                                        .length === 0 ? (
                                                        <p className="no-comments">
                                                            No comments yet.
                                                            Be the first to comment!
                                                        </p>
                                                    ) : (
                                                        <div className="my-comments-list">
                                                            {(comments[post.postId] || [])
                                                                .map((comment) => (
                                                                    <div
                                                                        className="my-comment"
                                                                        key={comment.commentId}
                                                                    >
                                                                        <div className="comment-avatar">
                                                                            {(comment.authorUsername || "U")
                                                                                .charAt(0)
                                                                                .toUpperCase()}
                                                                        </div>

                                                                        <div className="comment-content">
                                                                            <strong>
                                                                                {comment.authorUsername}
                                                                            </strong>

                                                                            {/* Comment editing */}
                                                                            {editingCommentId === comment.commentId ? (
                                                                                <div className="comment-edit-form">
                                                                                    <textarea
                                                                                        value={commentDraft}
                                                                                        onChange={(e) =>
                                                                                            setCommentDraft(e.target.value)
                                                                                        }
                                                                                        maxLength={1000}
                                                                                        rows={3}
                                                                                        disabled={savingComment}
                                                                                    />

                                                                                    <div className="comment-edit-actions">
                                                                                        <button
                                                                                            type="button"
                                                                                            onClick={cancelEditComment}
                                                                                            disabled={savingComment}
                                                                                        >
                                                                                            <X size={14} />
                                                                                            Cancel
                                                                                        </button>

                                                                                        <button
                                                                                            type="button"
                                                                                            onClick={() =>
                                                                                                handleUpdateComment(
                                                                                                    post.postId,
                                                                                                    comment.commentId
                                                                                                )
                                                                                            }
                                                                                            disabled={
                                                                                                savingComment ||
                                                                                                !commentDraft.trim()
                                                                                            }
                                                                                        >
                                                                                            <Check size={14} />
                                                                                            {savingComment
                                                                                                ? "Saving..."
                                                                                                : "Save"}
                                                                                        </button>
                                                                                    </div>
                                                                                </div>
                                                                            ) : (
                                                                                <p>{comment.content}</p>
                                                                            )}
                                                                        </div>

                                                                        {/* Only show controls for the comment author */}
                                                                        {comment.authorId === user?.userId && (
                                                                            <div className="comment-management-actions">
                                                                                <button
                                                                                    type="button"
                                                                                    onClick={() =>
                                                                                        startEditComment(comment)
                                                                                    }
                                                                                    disabled={
                                                                                        savingComment ||
                                                                                        editingCommentId !== null ||
                                                                                        deletingComments[comment.commentId]
                                                                                    }
                                                                                    title="Edit comment"
                                                                                >
                                                                                    <Pencil size={14} />
                                                                                </button>

                                                                                <button
                                                                                    type="button"
                                                                                    onClick={() =>
                                                                                        handleDeleteComment(
                                                                                            post.postId,
                                                                                            comment.commentId
                                                                                        )
                                                                                    }
                                                                                    disabled={
                                                                                        deletingComments[comment.commentId] ||
                                                                                        savingComment
                                                                                    }
                                                                                    title="Delete comment"
                                                                                >
                                                                                    <Trash2 size={14} />
                                                                                </button>
                                                                            </div>
                                                                        )}
                                                                    </div>
                                                                ))}
                                                        </div>
                                                    )}
                                                </>
                                            )}

                                            {/* Add comment form */}
                                            <form
                                                className="my-comment-form"
                                                onSubmit={(e) =>
                                                    addComment(post.postId, e)
                                                }
                                            >
                                                <input
                                                    type="text"
                                                    placeholder="Write a comment..."
                                                    value={
                                                        commentInputs[post.postId] || ""
                                                    }
                                                    onChange={(e) =>
                                                        setCommentInputs((prev) => ({
                                                            ...prev,
                                                            [post.postId]: e.target.value,
                                                        }))
                                                    }
                                                    maxLength={1000}
                                                />

                                                <button
                                                    type="submit"
                                                    disabled={
                                                        submittingComments[post.postId] ||
                                                        !commentInputs[post.postId]?.trim()
                                                    }
                                                >
                                                    {submittingComments[post.postId]
                                                        ? "Posting..."
                                                        : "Comment"}
                                                </button>
                                            </form>
                                        </div>
                                    )}
                                </article>
                            ))}
                        </div>
                    )}
                </section>
            </div>

            {/* Edit Profile Modal */}
            {editing && (
                <div className="profile-modal-overlay">
                    <div
                        className="profile-edit-modal"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="edit-profile-title"
                    >
                        <div className="profile-modal-header">
                            <h2 id="edit-profile-title">
                                Edit Profile
                            </h2>

                            <button
                                type="button"
                                className="profile-modal-close"
                                onClick={closeEditDialog}
                                disabled={saving}
                                aria-label="Close dialog"
                            >
                                <X size={20} />
                            </button>
                        </div>

                        <form onSubmit={handleSaveProfile}>
                            <label htmlFor="profile-bio">
                                Bio
                            </label>

                            <textarea
                                id="profile-bio"
                                value={bioDraft}
                                onChange={(e) =>
                                    setBioDraft(e.target.value)
                                }
                                placeholder="Tell people something about yourself..."
                                maxLength={250}
                                rows={5}
                            />

                            <div className="bio-character-count">
                                {bioDraft.length}/250 characters
                            </div>

                            {saveError && (
                                <p className="profile-error-message">
                                    {saveError}
                                </p>
                            )}

                            <div className="profile-modal-actions">
                                <button
                                    type="button"
                                    className="profile-cancel-button"
                                    onClick={closeEditDialog}
                                    disabled={saving}
                                >
                                    Cancel
                                </button>

                                <button
                                    type="submit"
                                    className="profile-save-button"
                                    disabled={saving}
                                >
                                    {saving
                                        ? "Saving..."
                                        : "Save Changes"}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </main>
    );
}
