package misc;

/**
 * Enumeration of the different types of messages.
 */
public enum MessageType {

    /**
     * Message is a request for a vote from a candidate node. The corresponding data will be the id
     * of the candidate node asking for a vote.
     */
    VOTE_REQUEST,

    /**
     * Message is a response to a candidate's vote request. The corresponding data will be a boolean
     * indicating whether the requesting candidate received the requestee's vote.
     */
    VOTE_RESPONSE,

    /**
     * Message is a request to append new entries to the local node's cache.
     */
    APPEND_ENTRIES,

    /**
     * Message is a response to an append entries request, verifying that we have received the
     * entries.
     */
    APPEND_ENTRIES_RESPONSE,

    /**
     * Message is a request to know who the current leader is.
     */
    FIND_LEADER,

    /**
     * Message is a request to commit cache to file
     */
    COMMIT
}
