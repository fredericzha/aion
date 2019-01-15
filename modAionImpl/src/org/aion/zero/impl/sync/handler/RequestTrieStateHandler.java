package org.aion.zero.impl.sync.handler;

import java.util.Map;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.TrieDatabase;
import org.aion.zero.impl.sync.msg.RequestTrieState;
import org.aion.zero.impl.sync.msg.ResponseTrieState;
import org.slf4j.Logger;

/**
 * Handler for trie node requests from the network.
 *
 * @author Alexandra Roatis
 */
public final class RequestTrieStateHandler extends Handler {

    private final Logger log;

    private final IAionBlockchain chain;

    private final IP2pMgr p2p;

    // limits the number of key-value pairs returned to one request
    public static final int MAXIMUM_BATCH_SIZE = 100;

    /**
     * Constructor.
     *
     * @param log logger for reporting execution information
     * @param chain the blockchain used by the application
     * @param p2p peer manager used to submit messages
     */
    public RequestTrieStateHandler(
            final Logger log, final IAionBlockchain chain, final IP2pMgr p2p) {
        super(Ver.V1, Ctrl.SYNC, Act.REQUEST_TRIE_STATE);
        this.log = log;
        this.chain = chain;
        this.p2p = p2p;
    }

    @Override
    public void receive(int peerId, String displayId, final byte[] message) {
        if (message == null || message.length == 0) {
            this.log.debug("<req-trie empty message from peer={}>", displayId);
            return;
        }

        RequestTrieState request = RequestTrieState.decode(message);

        if (request != null) {
            TrieDatabase dbType = request.getDbType();
            ByteArrayWrapper key = ByteArrayWrapper.wrap(request.getNodeKey());
            int limit = request.getLimit();

            if (log.isDebugEnabled()) {
                this.log.debug("<req-trie from-db={} key={} peer={}>", dbType, key, displayId);
            }

            // retrieve from blockchain depending on db type
            byte[] value = chain.getTrieNode(key.getData(), dbType);

            if (value != null) {
                ResponseTrieState response;

                if (limit == 1) {
                    // generate response without referenced nodes
                    response = new ResponseTrieState(key, value, dbType);
                } else {
                    // check for internal limit on the request
                    if (limit == 0) {
                        limit = MAXIMUM_BATCH_SIZE;
                    } else {
                        // the first value counts towards the limit
                        limit--;
                        limit = limit < MAXIMUM_BATCH_SIZE ? limit : MAXIMUM_BATCH_SIZE;
                    }

                    // determine if the node can be expanded
                    Map<ByteArrayWrapper, byte[]> referencedNodes =
                            chain.getReferencedTrieNodes(value, limit, dbType);

                    // generate response with referenced nodes
                    response = new ResponseTrieState(key, value, referencedNodes, dbType);
                }

                // reply to request
                this.p2p.send(peerId, displayId, response);
            }
        } else {
            this.log.error("<req-trie decode-error msg-bytes={} peer={}>", message.length, peerId);
        }
    }
}