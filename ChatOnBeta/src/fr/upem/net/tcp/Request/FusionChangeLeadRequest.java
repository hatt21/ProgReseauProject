package fr.upem.net.tcp.Request;

import fr.upem.net.tcp.Primitive.Request;

import java.nio.ByteBuffer;

public record FusionChangeLeadRequest(Byte version, ByteBuffer IpAddress, int port) implements Request {
    @Override
    public void fillBuffer(ByteBuffer buffer) {
        buffer.put((byte) 14);
        buffer.put(version).put(IpAddress).putInt(port);
    }
}
