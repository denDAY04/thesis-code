package edu.dk.asj.dpm.network.connections;

import edu.dk.asj.dpm.network.packets.IdentityPacket;
import edu.dk.asj.dpm.network.packets.Packet;
import edu.dk.asj.dpm.network.packets.SAEParameterPacket;
import edu.dk.asj.dpm.network.packets.SAETokenPacket;
import edu.dk.asj.dpm.security.SAEParameterSpec;
import edu.dk.asj.dpm.security.SAESession;
import edu.dk.asj.dpm.security.SecurityController;
import edu.dk.asj.dpm.util.BufferHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SAEConnection extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger(SAEConnection.class);

    private static final long SAE_HANDSHAKE_TIMEOUT_MS = 3000;
    private static final int SAE_BUFFER_CAPACITY = 1000;

    private final UUID nodeId;
    private final boolean isClient;
    private byte[] saeKey;

    protected AsynchronousSocketChannel connection;

    protected SAEConnection(String name, UUID nodeId, boolean isClient) {
        super(name);
        this.nodeId = nodeId;
        this.isClient = isClient;
    }

    protected SAEConnection(UUID nodeId, boolean isClient) {
        this.nodeId = nodeId;
        this.isClient = isClient;
    }

    protected boolean saeHandshake() {
        LOGGER.info("Initiating SAE handshake");

        UUID remoteId = exchangeIdentities();
        if (remoteId != null) {
            SAESession session = SecurityController.getInstance().initiateSaeSession(nodeId, remoteId);
            SAEParameterSpec remoteParameters = exchangeParameters(session.getParameters());
            if (remoteParameters != null) {
                byte[] token = SecurityController.getInstance().generateSAEToken(session, remoteParameters);
                byte[] remoteToken = exchangeTokens(token);
                if (remoteToken != null) {
                    saeKey = SecurityController.getInstance().validateSAEToken(session, remoteToken, remoteParameters);
                    return saeKey != null;
                }
            }
        }
        return false;
    }

    protected byte[] encrypt(byte[] data) throws Exception {
        LOGGER.debug("Encrypting data");
        return SecurityController.getInstance().encrypt(data, saeKey);
    }

    protected byte[] decrypt(byte[] data) throws Exception {
        LOGGER.debug("Decrypting data");
        return SecurityController.getInstance().decrypt(data, saeKey);
    }

    ///region Identities
    private UUID exchangeIdentities() {
        if (isClient)  {
            if (sendIdentity()) {
                return receiveIdentity();
            }
            return null;
        } else {
            UUID otherIdentity = receiveIdentity();
            if (otherIdentity != null) {
                sendIdentity();
            }
            return otherIdentity;
        }
    }

    private UUID receiveIdentity() {
        ByteBuffer idBuffer = ByteBuffer.allocate(SAE_BUFFER_CAPACITY);
        Future<Integer> receiveIdPromise = connection.read(idBuffer);
        int receivedBytes = -1;
        try {
            LOGGER.debug("Receiving SAE identity");
            receivedBytes =  receiveIdPromise.get(SAE_HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while receiving SAE identity");
        } catch (ExecutionException e) {
            LOGGER.warn("Unexpected exception while receiving SAE identity", e);
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while receiving SAE identity");
        }

        if (receivedBytes < 1) {
            return null;
        }

        Packet response = Packet.deserialize(BufferHelper.readAndClear(idBuffer));
        if (!(response instanceof IdentityPacket)) {
            LOGGER.warn("Received invalid SAE identity");
            return null;
        }
        return ((IdentityPacket) response).getNodeId();
    }

    private boolean sendIdentity() {
        Packet identityRequest = new IdentityPacket(nodeId);
        Future<Integer> sendIdPromise = connection.write(ByteBuffer.wrap(identityRequest.serialize()));
        int sentBytes = -1;
        try {
            LOGGER.debug("Sending SAE identity");
            sentBytes = sendIdPromise.get(SAE_HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while sending SAE identity");
        } catch (ExecutionException e) {
            LOGGER.warn("Unexpected exception while sending SAE identity", e);
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while sending SAE identity");
        }

        return sentBytes > 0;
    }
    ///endregion

    ///region Parameters
    private SAEParameterSpec exchangeParameters(SAEParameterSpec parameters) {
        if (isClient) {
            if (sendParameters(parameters)){
                return receiveParameters();
            }
            return null;
        } else {
            SAEParameterSpec otherParameters = receiveParameters();
            if (otherParameters != null) {
                sendParameters(parameters);
            }
            return otherParameters;
        }
    }

    private SAEParameterSpec receiveParameters() {
        ByteBuffer parameterBuffer = ByteBuffer.allocate(SAE_BUFFER_CAPACITY);
        Future<Integer> receiveParamPromise = connection.read(parameterBuffer);
        int receivedBytes = -1;
        try {
            LOGGER.debug("Receiving SAE parameters");
            receivedBytes = receiveParamPromise.get(SAE_HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while receiving SAE parameters");
        } catch (ExecutionException e) {
            LOGGER.warn("Unexpected exception while receiving SAE parameters", e);
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while receiving SAE parameters");
        }

        if (receivedBytes < 1) {
            return null;
        }

        Packet response = Packet.deserialize(BufferHelper.readAndClear(parameterBuffer));
        if (!(response instanceof SAEParameterPacket)) {
            LOGGER.warn("Received invalid SAE parameter response");
            return null;
        }
        return ((SAEParameterPacket) response).getParameters();
    }

    private boolean sendParameters(SAEParameterSpec parameters) {
        Packet request = new SAEParameterPacket(parameters);
        Future<Integer> sendParamPromise = connection.write(ByteBuffer.wrap(request.serialize()));
        int sentBytes = -1;
        try {
            LOGGER.debug("Sending SAE parameters");
            sentBytes = sendParamPromise.get(SAE_HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while sending SAE parameters");
        } catch (ExecutionException e) {
            LOGGER.warn("Unexpected exception while sending SAE parameters", e);
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while sending SAE parameters");
        }
        return sentBytes > 0;
    }
    ///endregion

    ///region Tokens
    private byte[] exchangeTokens(byte[] token) {
        if (isClient) {
            if (sendToken(token)){
                return receiveToken();
            }
            return null;
        } else {
            byte[] otherToken = receiveToken();
            if (otherToken != null) {
                sendToken(token);
            }
            return otherToken;
        }
    }

    private byte[] receiveToken() {
        ByteBuffer tokenBuffer = ByteBuffer.allocate(SAE_BUFFER_CAPACITY);
        Future<Integer> receiveTokenPromise = connection.read(tokenBuffer);
        int receivedBytes = -1;
        try {
            LOGGER.debug("Receiving SAE token");
            receivedBytes = receiveTokenPromise.get(SAE_HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while receiving SAE token");
        } catch (ExecutionException e) {
            LOGGER.warn("Unexpected exception while receiving SAE token", e);
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while receiving SAE token");
        }

        if (receivedBytes < 1) {
            return null;
        }

        Packet response = Packet.deserialize(BufferHelper.readAndClear(tokenBuffer));
        if (!(response instanceof SAETokenPacket)) {
            LOGGER.warn("Received invalid SAE token response");
            return null;
        }
        return ((SAETokenPacket) response).getToken();
    }

    private boolean sendToken(byte[] token) {
        Packet request = new SAETokenPacket(token);
        Future<Integer> sendTokenPromise = connection.write(ByteBuffer.wrap(request.serialize()));
        int sentBytes = -1;
        try {
            LOGGER.debug("Sending SAE token");
            sentBytes = sendTokenPromise.get(SAE_HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while sending SAE token");
        } catch (ExecutionException e) {
            LOGGER.warn("Unexpected exception while sending SAE token", e);
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while sending SAE token");
        }
        return sentBytes > 0;
    }
    ///endregion
}
