package Node;

public enum NodeType{

    /**
     * A Follower hears from a leader to receive information
     * If it does not hear from a Leader before it's timeout, then
     * it becomes a candidate for the leader election.
     * Followers reply to candidates with their vote
     */
    FOLLOWER,

    /**
     * Sends out request for a vote in leader election phase
     * (Note: It votes for itself)
     * Candidate tallies how many votes it received.
     * If it received majority votes, then it will become a leader
     * In the case where Candidates are tied in votes, then no one
     * becomes a leader, and the leader election starts over again
     */
    CANDIDATE,

    /**
     * Changes are sent to leader nodes
     * The Leader stores the uncommitted changes in its log
     * Then it replicates the changes to follower nodes
     * Leader waits for responses from majority of followers
     * Leader commits the change and notifies followers that
     * the change is committed, therefore consensus is reached
     */
    LEADER
}