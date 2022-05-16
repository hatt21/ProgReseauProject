package fr.upem.net.tcp.Request;

import fr.upem.net.tcp.Primitive.Request;

import java.nio.ByteBuffer;

public record FusionRequ13Request(Byte status)implements Request {
    @Override
    public void fillBuffer(ByteBuffer buffer) {
        buffer.put((byte)13);
        buffer.put(status);
    }
}
