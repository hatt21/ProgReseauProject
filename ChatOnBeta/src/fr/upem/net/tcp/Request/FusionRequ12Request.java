package fr.upem.net.tcp.Request;

import fr.upem.net.tcp.Primitive.Request;

import java.nio.ByteBuffer;

public record FusionRequ12Request(Byte version, ByteBuffer IpAddress, int port) implements Request {
    @Override
    public void fillBuffer(ByteBuffer buffer) {
        buffer.put((byte) 12);
        buffer.put(version).put(IpAddress).putInt(port);
    }
}
