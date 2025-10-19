package com.zanable.marketmaking.bot.exchangeintegration;

import com.google.gson.Gson;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mxc.push.common.protobuf.PublicLimitDepthsV3Api;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;
import com.zanable.marketmaking.bot.services.ZanoPriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;

@ClientEndpoint
public class MexcWebsocketClientEndpoint {

    private final static Logger logger = LoggerFactory.getLogger(MexcWebsocketClientEndpoint.class);
    Session userSession = null;
    private MessageHandler messageHandler;
    private Gson gson = new Gson();

    public MexcWebsocketClientEndpoint(URI endpointURI) {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, endpointURI);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * Callback hook for Connection open events.
     *
     * @param userSession the userSession which is opened.
     */
    @OnOpen
    public void onOpen(Session userSession) {
        logger.info("opening websocket mexc");
        this.userSession = userSession;
    }

    /**
     * Callback hook for Connection close events.
     *
     * @param userSession the userSession which is getting closed.
     * @param reason the reason for connection close
     */
    @OnClose
    public void onClose(Session userSession, CloseReason reason) {
        logger.info("Closing websocket to Mexc");
        this.userSession = null;
        this.messageHandler.handleDisconnect();
    }

    /**
     * Callback hook for Message Events. This method will be invoked when a client send a message.
     *
     * @param message The text message
     */
    @OnMessage
    public void onMessage(String message) {
        if (this.messageHandler != null) {
            this.messageHandler.handleMessage(message);
        }
    }

    @OnMessage
    public void onMessage(ByteBuffer bytes) {
        String newContent = null;

        PushDataV3ApiWrapper apiWrapper = null;
        try {
            apiWrapper = deserializeProto(bytes);
            if (apiWrapper.getBodyCase().name().equals("PUBLICLIMITDEPTHS")) {
                PublicLimitDepthsV3Api orderBook = apiWrapper.getPublicLimitDepths();
                this.messageHandler.handleOrderBookUpdate(orderBook);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // this.messageHandler.handleMessage(newContent);
    }

    public static PushDataV3ApiWrapper deserializeProto(ByteBuffer input) throws InvalidProtocolBufferException {
        PushDataV3ApiWrapper apiWrapper = PushDataV3ApiWrapper.parseFrom(input.array());
        return apiWrapper;
    }

    /**
     * register message handler
     *
     * @param msgHandler
     */
    public void addMessageHandler(MessageHandler msgHandler) {
        this.messageHandler = msgHandler;
    }

    /**
     * Send a message.
     *
     * @param message
     */
    public void sendMessage(String message) {
        this.userSession.getAsyncRemote().sendText(message);
    }

    public void close() throws IOException {
        userSession.close();
    }

    public boolean isOpen() {
        if (this.userSession == null) {
            return false;
        }
        return this.userSession.isOpen();
    }

    /**
     * Message handler.
     *
     * @author Jiji_Sasidharan
     */
    public static interface MessageHandler {

        public void handleMessage(String message);
        public void handleDisconnect();
        public void handleOrderBookUpdate(PublicLimitDepthsV3Api orderBook);
    }

}
