
import { useCallback, useEffect, useMemo, useState } from "react";
import { Search, Users, UserPlus, UserCheck, RefreshCw } from "lucide-react";
import { sendRequest } from "../services/api";
import { useAuth } from "../context/AuthContext";

export default function Explore() {
    const { user, sessionToken } = useAuth();

    const [users, setUsers] = useState([]);
    const [search, setSearch] = useState("");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [updatingUsers, setUpdatingUsers] = useState({});

    const loadUsers = useCallback(async () => {
        try {
            setLoading(true);
            setError("");

            const response = await sendRequest("GET_USERS", {
                sessionToken,
            });

            if (response.status !== "SUCCESS") {
                throw new Error(response.message || "Failed to load users");
            }

            setUsers(response.users || []);
        } catch (err) {
            setError(err.message || "Unable to load users");
        } finally {
            setLoading(false);
        }
    }, [sessionToken]);

    useEffect(() => {
        loadUsers();
    }, [loadUsers]);

    const filteredUsers = useMemo(() => {
        const query = search.trim().toLowerCase();

        return users.filter((item) =>
            item.username?.toLowerCase().includes(query)
        );
    }, [users, search]);

    const toggleFollow = async (targetUser) => {
        const targetUserId = targetUser.userId;

        if (updatingUsers[targetUserId]) return;

        const command = targetUser.isFollowing
            ? "UNFOLLOW_USER"
            : "FOLLOW_USER";

        try {
            setUpdatingUsers((previous) => ({
                ...previous,
                [targetUserId]: true,
            }));

            const response = await sendRequest(command, {
                sessionToken,
                targetUserId,
            });

            if (response.status !== "SUCCESS") {
                throw new Error(response.message || "Follow action failed");
            }

            setUsers((previous) =>
                previous.map((item) =>
                    item.userId === targetUserId
                        ? {
                              ...item,
                              isFollowing: !targetUser.isFollowing,
                              followersCount: Math.max(
                                  0,
                                  (item.followersCount || 0) +
                                      (targetUser.isFollowing ? -1 : 1)
                              ),
                          }
                        : item
                )
            );
        } catch (err) {
            setError(err.message || "Unable to update follow status");
        } finally {
            setUpdatingUsers((previous) => ({
                ...previous,
                [targetUserId]: false,
            }));
        }
    };

    return (
        <main className="explore-page">
            <section className="explore-header">
                <div>
                    <span className="explore-eyebrow">COMMUNITY</span>
                    <h1>Discover People</h1>
                    <p>Find people, connect, and grow your network.</p>
                </div>

                <div className="explore-header-icon">
                    <Users size={28} />
                </div>
            </section>

            <section className="explore-search">
                <Search size={20} />
                <input
                    type="text"
                    placeholder="Search people by username..."
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                />
                <span>{filteredUsers.length} people</span>
            </section>

            {error && (
                <div className="explore-error">
                    {error}
                    <button onClick={loadUsers}>Try again</button>
                </div>
            )}

            {loading ? (
                <div className="explore-state">
                    <RefreshCw className="spin" size={25} />
                    <p>Finding people...</p>
                </div>
            ) : filteredUsers.length === 0 ? (
                <div className="explore-state">
                    <Users size={35} />
                    <h3>No users found</h3>
                    <p>
                        {search
                            ? "Try searching with another username."
                            : "There are no other users to discover yet."}
                    </p>
                </div>
            ) : (
                <section className="explore-user-grid">
                    {filteredUsers.map((person) => (
                        <article className="explore-user-card" key={person.userId}>
                            <div className="explore-user-avatar">
                                {person.username?.charAt(0).toUpperCase()}
                            </div>

                            <h3>{person.username}</h3>

                            <p className="explore-user-bio">
                                {person.bio?.trim() || "No bio added yet."}
                            </p>

                            <div className="explore-user-stats">
                                <div>
                                    <strong>
                                        {person.followersCount || 0}
                                    </strong>
                                    <span>Followers</span>
                                </div>

                                <div>
                                    <strong>
                                        {person.followingCount || 0}
                                    </strong>
                                    <span>Following</span>
                                </div>
                            </div>

                            <button
                                className={
                                    person.isFollowing
                                        ? "explore-follow-btn following"
                                        : "explore-follow-btn"
                                }
                                onClick={() => toggleFollow(person)}
                                disabled={!!updatingUsers[person.userId]}
                            >
                                {person.isFollowing ? (
                                    <UserCheck size={17} />
                                ) : (
                                    <UserPlus size={17} />
                                )}

                                {updatingUsers[person.userId]
                                    ? "Please wait..."
                                    : person.isFollowing
                                      ? "Following"
                                      : "Follow"}
                            </button>
                        </article>
                    ))}
                </section>
            )}
        </main>
    );
}
