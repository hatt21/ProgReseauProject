package fr.upem.net.tcp.Request;

import fr.upem.net.tcp.Primitive.Request;

import java.nio.ByteBuffer;
import java.util.Objects;

public record LogAnoRequest(String login) implements Request {

    @Override
    public void fillBuffer(ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        buffer.put((byte) 0);
        encode(buffer,30,login);
    }
}
