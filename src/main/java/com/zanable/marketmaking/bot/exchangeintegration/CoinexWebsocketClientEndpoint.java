package com.zanable.marketmaking.bot.exchangeintegration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;

@ClientEndpoint
public class CoinexWebsocketClientEndpoint {

    private final static Logger logger = LoggerFactory.getLogger(CoinexWebsocketClientEndpoint.class);
    Session userSession = null;
    private MessageHandler messageHandler;

    public CoinexWebsocketClientEndpoint(URI endpointURI) {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, endpointURI);
        } catch (Exception e) {
            e.printStackTrace();
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
        logger.info("opening websocket coinex");
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
        logger.info("Closing websocket to Coinex");
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
        try {
            newContent = unzip(bytes);
            this.messageHandler.handleMessage(newContent);
        } catch (DataFormatException e) {
            throw new RuntimeException(e);
        }
    }

    public static String unzip(ByteBuffer input) throws DataFormatException {

        try {
            GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(input.array()));
            InputStreamReader reader = new InputStreamReader(gzis);
            BufferedReader in = new BufferedReader(reader);

            String read;
            while ((read = in.readLine()) != null) {
                return read;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        throw new DataFormatException();
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
    public void sendMessage(String message) throws IOException {
        this.userSession.getBasicRemote().sendText(message);
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
    }

}
