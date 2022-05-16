package fr.upem.net.tcp.Request;

import fr.upem.net.tcp.Primitive.Request;

import java.nio.ByteBuffer;

public record FusionFwdRequest(Byte version,ByteBuffer IpAddress,int port) implements Request {
    @Override
    public void fillBuffer(ByteBuffer buffer) {
        buffer.put((byte) 11);
        buffer.put(version).put(IpAddress).putInt(port);
    }
}
