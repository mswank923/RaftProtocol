package misc;

public enum MessageType {
    /**
     * misc.Message is a request for a vote from a candidate node. The corresponding data will be the id
     * of the candidate node asking for a vote.
     */
    VOTE_REQUEST,


    /**
     * misc.Message is a response to a candidate's vote request. The corresponding data will be the id
     * of the node voted for.
     */
    VOTE_RESPONSE,

    /**
     * misc.Message is a request to append new entries to the local node's cache.
     */
    APPEND_ENTRIES,

    /**
     * misc.Message is a response to an append entries request, verifying that we have received the
     * entries.
     */
    APPEND_ENTRIES_RESPONSE
}
