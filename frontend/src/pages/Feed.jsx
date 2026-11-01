
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
    Heart,
    MessageCircle,
    Send,
    RefreshCw,
    ChevronDown,
    ChevronUp,
    Pencil,
    Trash2,
    Check,
    X,
} from "lucide-react";
import { sendRequest } from "../services/api";
import { useAuth } from "../context/AuthContext";
import { NavLink } from "react-router-dom";
export default function Feed() {
    const { user, sessionToken, logout } = useAuth();
    const navigate = useNavigate();

    const [posts, setPosts] = useState([]);
    const [content, setContent] = useState("");
    const [loading, setLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [posting, setPosting] = useState(false);
    const [error, setError] = useState("");
    const [likingPosts, setLikingPosts] = useState([]);
    const [comments, setComments] = useState({});
    const [commentInputs, setCommentInputs] = useState({});
    const [expandedComments, setExpandedComments] = useState({});
    const [commentLoading, setCommentLoading] = useState({});
    const [submittingComments, setSubmittingComments] = useState({});
    const [composerOpen, setComposerOpen] = useState(false);
    // Post editing and deletion
    const [editingPostId, setEditingPostId] = useState(null);
    const [postDraft, setPostDraft] = useState("");
    const [savingPost, setSavingPost] = useState(false);
    const [deletingPosts, setDeletingPosts] = useState({});

    // Comment editing and deletion
    const [editingCommentId, setEditingCommentId] = useState(null);
    const [commentDraft, setCommentDraft] = useState("");
    const [savingComment, setSavingComment] = useState(false);
    const [deletingComments, setDeletingComments] = useState({});
    // Fetch feed without hiding existing posts.
    async function loadFeed(isInitialLoad = false) {
        if (isInitialLoad) {
            setLoading(true);
        } else {
            setRefreshing(true);
        }

        try {
            setError("");

            const result = await sendRequest("GET_FEED", {
                sessionToken,
            });

            if (result.status !== "SUCCESS") {
                throw new Error(result.message || "Unable to load feed");
            }

            const feedPosts = result.posts ?? [];

            // Preserve the existing comment counts until refreshed.
            setPosts((previousPosts) =>
                feedPosts.map((post) => {
                    const existingPost = previousPosts.find(
                        (item) => item.postId === post.postId
                    );

                    return {
                        ...post,
                        commentCount:
                            comments[post.postId]?.length ??
                            existingPost?.commentCount ??
                            0,
                    };
                })
            );

            // Fetch comment counts for every post from the backend.
            const commentResults = await Promise.all(
                feedPosts.map(async (post) => {
                    try {
                        const response = await sendRequest("GET_COMMENTS", {
                            sessionToken,
                            postId: post.postId,
                        });

                        if (response.status === "SUCCESS") {
                            return {
                                postId: post.postId,
                                comments: response.comments ?? [],
                            };
                        }
                    } catch (err) {
                        console.error(
                            "Comment count fetch failed:",
                            post.postId,
                            err
                        );
                    }

                    return null;
                })
            );

            const validResults = commentResults.filter(Boolean);

            const updatedComments = {};

            validResults.forEach(({ postId, comments: postComments }) => {
                updatedComments[postId] = postComments;
            });

            setComments((previous) => ({
                ...previous,
                ...updatedComments,
            }));

            setPosts((previousPosts) =>
                previousPosts.map((post) => ({
                    ...post,
                    commentCount:
                        updatedComments[post.postId]?.length ??
                        post.commentCount ??
                        0,
                }))
            );
        } catch (err) {
            console.error("Feed loading error:", err);
            setError(err.message);
        } finally {
            setLoading(false);
            setRefreshing(false);
        }
    }

    useEffect(() => {
        loadFeed(true);
    }, []);

    // Create a post.
    async function createPost(e) {
        e.preventDefault();

        if (!content.trim() || posting) return;

        try {
            setPosting(true);
            setError("");

            const result = await sendRequest("CREATE_POST", {
                sessionToken,
                content: content.trim(),
            });

            if (result.status !== "SUCCESS") {
                throw new Error(result.message || "Could not create post");
            }

            setContent("");
            setComposerOpen(false);
            await loadFeed();
        } catch (err) {
            setError(err.message);
        } finally {
            setPosting(false);
        }
    }

    // Like or unlike a post.
    async function toggleLike(post) {
        if (likingPosts.includes(post.postId)) return;

        const alreadyLiked = post.isLiked;

        try {
            setError("");

            setLikingPosts((current) => [...current, post.postId]);

            const result = await sendRequest(
                alreadyLiked ? "UNLIKE_POST" : "LIKE_POST",
                {
                    sessionToken,
                    postId: post.postId,
                }
            );

            if (result.status !== "SUCCESS") {
                throw new Error(result.message || "Like action failed");
            }

            // Update only the selected post.
            setPosts((currentPosts) =>
                currentPosts.map((item) =>
                    item.postId === post.postId
                        ? {
                            ...item,
                            isLiked: !alreadyLiked,
                            likesCount: Math.max(
                                0,
                                (item.likesCount ?? 0) + (alreadyLiked ? -1 : 1)
                            ),
                        }
                        : item
                )
            );
        } catch (err) {
            console.error("Like error:", err);
            setError(err.message);
        } finally {
            setLikingPosts((current) =>
                current.filter((id) => id !== post.postId)
            );
        }
    }
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
                throw new Error(result.message || "Unable to load comments");
            }

            setComments((prev) => ({
                ...prev,
                [postId]: result.comments ?? [],
            }));
        } catch (err) {
            console.error("Comment loading error:", err);
            setError(err.message);
        } finally {
            setCommentLoading((prev) => ({
                ...prev,
                [postId]: false,
            }));
        }
    }
    async function toggleComments(postId) {
        const isExpanded = expandedComments[postId];

        setExpandedComments((prev) => ({
            ...prev,
            [postId]: !isExpanded,
        }));

        // Fetch comments when opening the section.
        if (!isExpanded) {
            await loadComments(postId);
        }
    }
    async function addComment(postId, e) {
        e.preventDefault();

        const commentText = (commentInputs[postId] || "").trim();

        if (!commentText || submittingComments[postId]) return;

        try {
            setSubmittingComments((prev) => ({
                ...prev,
                [postId]: true,
            }));

            setError("");

            const result = await sendRequest("ADD_COMMENT", {
                sessionToken,
                postId,
                content: commentText,
            });

            if (result.status !== "SUCCESS") {
                throw new Error(result.message || "Unable to add comment");
            }

            setCommentInputs((prev) => ({
                ...prev,
                [postId]: "",
            }));

            // Fetch the authoritative comment list from Java.
            const commentsResult = await sendRequest("GET_COMMENTS", {
                sessionToken,
                postId,
            });

            if (commentsResult.status !== "SUCCESS") {
                throw new Error(
                    commentsResult.message || "Unable to retrieve updated comments"
                );
            }

            const updatedComments = commentsResult.comments ?? [];

            setComments((prev) => ({
                ...prev,
                [postId]: updatedComments,
            }));

            // Keep the feed count synchronized with the actual comment list.
            setPosts((prev) =>
                prev.map((post) =>
                    post.postId === postId
                        ? {
                            ...post,
                            commentCount: updatedComments.length,
                        }
                        : post
                )
            );
        } catch (err) {
            console.error("Add comment error:", err);
            setError(err.message);
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
        const updatedContent = postDraft.trim();

        if (!updatedContent) {
            alert("Post content cannot be empty");
            return;
        }

        if (updatedContent.length > 2000) {
            alert("Post cannot exceed 2000 characters");
            return;
        }

        try {
            setSavingPost(true);

            const result = await sendRequest("UPDATE_POST", {
                sessionToken,
                postId,
                content: updatedContent,
            });

            if (result.status !== "SUCCESS") {
                throw new Error(result.message || "Unable to update post");
            }

            setPosts((prev) =>
                prev.map((post) =>
                    post.postId === postId
                        ? {
                            ...post,
                            content: result.post?.content ?? updatedContent,
                        }
                        : post
                )
            );

            setEditingPostId(null);
            setPostDraft("");
        } catch (err) {
            console.error("Update post error:", err);
            setError(err.message || "Unable to update post");
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
                throw new Error(result.message || "Unable to delete post");
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
            setError(err.message || "Unable to delete post");
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
        const updatedContent = commentDraft.trim();

        if (!updatedContent) {
            alert("Comment cannot be empty");
            return;
        }

        if (updatedContent.length > 1000) {
            alert("Comment cannot exceed 1000 characters");
            return;
        }

        try {
            setSavingComment(true);

            const result = await sendRequest("UPDATE_COMMENT", {
                sessionToken,
                commentId,
                content: updatedContent,
            });

            if (result.status !== "SUCCESS") {
                throw new Error(result.message || "Unable to update comment");
            }

            setComments((prev) => ({
                ...prev,
                [postId]: (prev[postId] || []).map((comment) =>
                    comment.commentId === commentId
                        ? {
                            ...comment,
                            content:
                                result.comment?.content ?? updatedContent,
                        }
                        : comment
                ),
            }));

            setEditingCommentId(null);
            setCommentDraft("");
        } catch (err) {
            console.error("Update comment error:", err);
            setError(err.message || "Unable to update comment");
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
                throw new Error(result.message || "Unable to delete comment");
            }

            setComments((prev) => {
                const updatedComments = (prev[postId] || []).filter(
                    (comment) => comment.commentId !== commentId
                );

                return {
                    ...prev,
                    [postId]: updatedComments,
                };
            });

            setPosts((prev) =>
                prev.map((post) =>
                    post.postId === postId
                        ? {
                            ...post,
                            commentCount: Math.max(
                                0,
                                (post.commentCount ?? 0) - 1
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
            setError(err.message || "Unable to delete comment");
        } finally {
            setDeletingComments((prev) => ({
                ...prev,
                [commentId]: false,
            }));
        }
    }
    function handleLogout() {
        logout();
        navigate("/login");
    }

    return (
        <div className="feed-page">


            <main className="feed-layout">

                {/* Left sidebar */}
                <aside className="feed-sidebar left-sidebar">
                    <div className="sidebar-user">
                        <div className="avatar">
                            {(user?.username || "U").charAt(0).toUpperCase()}
                        </div>

                        <strong>{user?.username || "User"}</strong>
                    </div>

                    <nav className="sidebar-navigation">
                        <NavLink to="/profile">
                            <span className="sidebar-icon">👤</span>
                            My Profile
                        </NavLink>

                        <NavLink to="/feed">
                            <span className="sidebar-icon">🏠</span>
                            News Feed
                        </NavLink>

                        <NavLink to="/explore">
                            <span className="sidebar-icon">🌍</span>
                            Explore People
                        </NavLink>

                        <NavLink to="/notifications">
                            <span className="sidebar-icon">🔔</span>
                            Notifications
                        </NavLink>
                    </nav>
                </aside>

                {/* Center feed */}
                <div className="feed-main">
                    <section className="composer-card">
                        {!composerOpen ? (
                            <button
                                type="button"
                                className="composer-trigger"
                                onClick={() => setComposerOpen(true)}
                            >
                                <div className="avatar">
                                    {(user?.username || "U").charAt(0).toUpperCase()}
                                </div>

                                <span>What's on your mind?</span>
                            </button>
                        ) : (
                            <>
                                <div className="composer-heading">
                                    <h3>Create a post</h3>

                                    <button
                                        type="button"
                                        className="composer-close"
                                        onClick={() => {
                                            setComposerOpen(false);
                                            setContent("");
                                        }}
                                    >
                                        ×
                                    </button>
                                </div>

                                <form onSubmit={createPost}>
                                    <textarea
                                        autoFocus
                                        value={content}
                                        onChange={(e) => setContent(e.target.value)}
                                        placeholder="What's on your mind?"
                                        maxLength={2000}
                                        rows={4}
                                    />

                                    <div className="composer-footer">
                                        <span>{content.length}/2000</span>

                                        <button
                                            type="submit"
                                            disabled={posting || !content.trim()}
                                        >
                                            <Send size={16} />
                                            {posting ? "Posting..." : "Post"}
                                        </button>
                                    </div>
                                </form>
                            </>
                        )}
                    </section>

                    {error && (
                        <div className="error-message">
                            {error}
                        </div>
                    )}

                    <div className="feed-title">
                        <h3>Recent Posts</h3>

                        <button
                            type="button"
                            onClick={() => loadFeed(false)}
                            className="refresh-button"
                            disabled={refreshing}
                        >
                            <RefreshCw
                                size={16}
                                className={refreshing ? "refresh-spinning" : ""}
                            />
                            {refreshing ? "Refreshing..." : "Refresh"}
                        </button>
                    </div>

                    {loading ? (
                        <p className="empty-message">Loading posts...</p>
                    ) : posts.length === 0 ? (
                        <p className="empty-message">
                            No posts yet. Create your first post!
                        </p>
                    ) : (
                        <section className="posts-list">
                            {posts.map((post) => (
                                <article
                                    className="post-card"
                                    key={post.postId}
                                >
                                    <div className="post-author">
                                        <div className="avatar">
                                            {(post.authorUsername || "U")
                                                .charAt(0)
                                                .toUpperCase()}
                                        </div>

                                        <div>
                                            <strong>
                                                {post.authorUsername || "Unknown User"}
                                            </strong>

                                            <small>
                                                {post.createdAt
                                                    ? new Date(post.createdAt).toLocaleString()
                                                    : ""}
                                            </small>
                                        </div>

                                        {/* Show management buttons only for the post author */}
                                        {post.authorId === user?.userId && (
                                            <div className="feed-management-actions">
                                                <button
                                                    type="button"
                                                    className="feed-edit-button"
                                                    onClick={() => startEditPost(post)}
                                                    disabled={
                                                        editingPostId === post.postId ||
                                                        savingPost ||
                                                        deletingPosts[post.postId]
                                                    }
                                                    title="Edit post"
                                                >
                                                    <Pencil size={14} />
                                                    Edit
                                                </button>

                                                <button
                                                    type="button"
                                                    className="feed-delete-button"
                                                    onClick={() => handleDeletePost(post.postId)}
                                                    disabled={
                                                        deletingPosts[post.postId] || savingPost
                                                    }
                                                    title="Delete post"
                                                >
                                                    <Trash2 size={14} />
                                                    {deletingPosts[post.postId]
                                                        ? "Deleting..."
                                                        : "Delete"}
                                                </button>
                                            </div>
                                        )}
                                    </div>

                                    {/* Post content or edit form */}
                                    {editingPostId === post.postId ? (
                                        <div className="feed-post-edit-form">
                                            <textarea
                                                value={postDraft}
                                                onChange={(e) => setPostDraft(e.target.value)}
                                                maxLength={2000}
                                                rows={4}
                                                disabled={savingPost}
                                            />

                                            <div className="feed-edit-footer">
                                                <span>{postDraft.length}/2000</span>

                                                <div>
                                                    <button
                                                        type="button"
                                                        onClick={cancelEditPost}
                                                        disabled={savingPost}
                                                    >
                                                        <X size={14} />
                                                        Cancel
                                                    </button>

                                                    <button
                                                        type="button"
                                                        onClick={() => handleUpdatePost(post.postId)}
                                                        disabled={savingPost || !postDraft.trim()}
                                                    >
                                                        <Check size={14} />
                                                        {savingPost ? "Saving..." : "Save"}
                                                    </button>
                                                </div>
                                            </div>
                                        </div>
                                    ) : (
                                        <p className="post-content">
                                            {post.content}
                                        </p>
                                    )}

                                    <div className="post-stats">
                                        <span>
                                            <Heart size={16} fill="#1877f2" color="#1877f2" />
                                            {post.likesCount ?? 0}
                                        </span>

                                        <button
                                            type="button"
                                            onClick={() => toggleComments(post.postId)}
                                        >
                                            {comments[post.postId]?.length ?? post.commentCount ?? 0} Comments
                                        </button>
                                    </div>

                                    <div className="post-actions">
                                        <button
                                            type="button"
                                            onClick={() => toggleLike(post)}
                                            disabled={likingPosts.includes(post.postId)}
                                            className={post.isLiked ? "liked" : ""}
                                        >
                                            <Heart
                                                size={19}
                                                fill={post.isLiked ? "currentColor" : "none"}
                                            />
                                            Like
                                        </button>

                                        <button
                                            type="button"
                                            onClick={() => toggleComments(post.postId)}
                                            className="comment-action"
                                        >
                                            <MessageCircle size={19} />
                                            Comment
                                        </button>
                                    </div>
                                    {expandedComments[post.postId] && (
                                        <section className="comments-section">
                                            <h4>Comments</h4>

                                            {commentLoading[post.postId] ? (
                                                <p className="comments-empty">Loading comments...</p>
                                            ) : (
                                                <>
                                                    {(comments[post.postId] ?? []).length === 0 ? (
                                                        <p className="comments-empty">
                                                            No comments yet. Be the first to comment!
                                                        </p>
                                                    ) : (
                                                        <div className="comments-list">
                                                            {comments[post.postId].map((comment) => (
                                                                <div
                                                                    className="comment-item"
                                                                    key={comment.commentId}
                                                                >
                                                                    <div className="comment-avatar">
                                                                        {(comment.authorUsername || "U")
                                                                            .charAt(0)
                                                                            .toUpperCase()}
                                                                    </div>

                                                                    <div className="comment-content">
                                                                        <strong>
                                                                            {comment.authorUsername || "Unknown User"}
                                                                        </strong>

                                                                        {editingCommentId === comment.commentId ? (
                                                                            <div className="feed-comment-edit-form">
                                                                                <textarea
                                                                                    value={commentDraft}
                                                                                    onChange={(e) => setCommentDraft(e.target.value)}
                                                                                    maxLength={1000}
                                                                                    rows={3}
                                                                                    disabled={savingComment}
                                                                                />

                                                                                <div className="feed-comment-edit-actions">
                                                                                    <button
                                                                                        type="button"
                                                                                        onClick={cancelEditComment}
                                                                                        disabled={savingComment}
                                                                                    >
                                                                                        <X size={13} />
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
                                                                                        disabled={savingComment || !commentDraft.trim()}
                                                                                    >
                                                                                        <Check size={13} />
                                                                                        {savingComment ? "Saving..." : "Save"}
                                                                                    </button>
                                                                                </div>
                                                                            </div>
                                                                        ) : (
                                                                            <p>{comment.content}</p>
                                                                        )}

                                                                        <small>
                                                                            {comment.createdAt
                                                                                ? new Date(comment.createdAt).toLocaleString()
                                                                                : ""}
                                                                        </small>
                                                                    </div>

                                                                    {/* Only the comment author can see these controls */}
                                                                    {comment.authorId === user?.userId && (
                                                                        <div className="feed-comment-management">
                                                                            <button
                                                                                type="button"
                                                                                title="Edit comment"
                                                                                onClick={() => startEditComment(comment)}
                                                                                disabled={
                                                                                    editingCommentId !== null ||
                                                                                    savingComment ||
                                                                                    deletingComments[comment.commentId]
                                                                                }
                                                                            >
                                                                                <Pencil size={13} />
                                                                            </button>

                                                                            <button
                                                                                type="button"
                                                                                title="Delete comment"
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
                                                                            >
                                                                                <Trash2 size={13} />
                                                                            </button>
                                                                        </div>
                                                                    )}
                                                                </div>
                                                            ))}
                                                        </div>
                                                    )}
                                                </>
                                            )}

                                            <form
                                                className="comment-form"
                                                onSubmit={(e) => addComment(post.postId, e)}
                                            >
                                                <input
                                                    type="text"
                                                    placeholder="Write a comment..."
                                                    maxLength={1000}
                                                    value={commentInputs[post.postId] ?? ""}
                                                    onChange={(e) =>
                                                        setCommentInputs((prev) => ({
                                                            ...prev,
                                                            [post.postId]: e.target.value,
                                                        }))
                                                    }
                                                />

                                                <button
                                                    type="submit"
                                                    disabled={
                                                        submittingComments[post.postId] ||
                                                        !(commentInputs[post.postId] ?? "").trim()
                                                    }
                                                >
                                                    <Send size={16} />
                                                    {submittingComments[post.postId] ? "Sending..." : "Send"}
                                                </button>
                                            </form>
                                        </section>
                                    )}
                                </article>
                            ))}
                        </section>
                    )}
                </div>

                {/* Right sidebar */}
                <aside className="feed-sidebar right-sidebar">
                    <div className="sidebar-panel">
                        <h3>MiniFacebook</h3>

                        <p>
                            Connect with people, share updates, and interact
                            with posts in your community.
                        </p>
                    </div>

                    <div className="sidebar-panel">
                        <h4>Quick Navigation</h4>

                        <NavLink to="/profile">View your profile</NavLink>
                        <NavLink to="/explore">Discover people</NavLink>
                        <NavLink to="/notifications">Check notifications</NavLink>
                    </div>

                    <p className="sidebar-footer">
                        MiniFacebook · Distributed Computing Project
                    </p>
                </aside>

            </main>
        </div>
    );
}
