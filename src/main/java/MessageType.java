public enum MessageType {
    /**
     * Message is a periodic heartbeat from the leader node indicating to its followers that it is
     * still present and active. The corresponding data will be the id of the source (leader) node.
     */
    HEARTBEAT,


    /**
     * Message is a request for a vote from a candidate node. The corresponding data will be the id
     * of the candidate node asking for a vote.
     */
    VOTE_REQUEST,


    /**
     * Message is a response to a candidate's vote request. The corresponding data will be the id
     * of the node voted for.
     */
    VOTE_RESPONSE,


    APPEND_ENTRIES,


    APPEND_ENTRIES_RESPONSE

}
