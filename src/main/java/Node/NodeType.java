package Node;

/**
 * Enumeration of a type of node.
 */
public enum NodeType{

    /**
     * A Follower hears from a leader to receive information. If it does not hear from a Leader
     * before its timeout, then it becomes a candidate in the leader election. Followers reply to
     * candidates with their vote, determining the leader.
     */
    FOLLOWER,

    /**
     * Sends out request for a vote in leader election phase. Candidate tallies how many votes it
     * received (Note: It votes for itself). If it received majority votes, then it will become a
     * leader. In the case where candidates are tied in votes, then no one becomes a leader, and the
     * leader election is left to start over again.
     */
    CANDIDATE,

    /**
     * Changes are sent to leader node. The leader stores the uncommitted changes in its log, then
     * replicates the changes to follower nodes. Leader waits for responses from majority of
     * followers, then commits the change and notifies followers that the change is committed,
     * therefore consensus is reached.
     */
    LEADER
}