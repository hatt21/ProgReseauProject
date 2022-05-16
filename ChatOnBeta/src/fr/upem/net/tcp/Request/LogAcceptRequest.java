package fr.upem.net.tcp.Request;

import fr.upem.net.tcp.Primitive.Request;

import java.nio.ByteBuffer;
import java.util.Objects;

public record LogAcceptRequest(String servername) implements Request {

    @Override
    public void fillBuffer(ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        buffer.put((byte) 2);
        encode(buffer,100,servername);
    }
}
