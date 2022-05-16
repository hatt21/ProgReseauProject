package fr.upem.net.tcp.Request;

import fr.upem.net.tcp.Primitive.Request;

import java.nio.ByteBuffer;
import java.util.Objects;

public record MessagePubRequest(String serverName, String login, String msg) implements Request {

    @Override
    public void fillBuffer(ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        buffer.put((byte) 4);
        encode(buffer,100, serverName);
        encode(buffer,100, login);
        encode(buffer,1024, msg);
    }
}
