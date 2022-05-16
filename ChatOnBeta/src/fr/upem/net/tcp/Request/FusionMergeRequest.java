package fr.upem.net.tcp.Request;

import fr.upem.net.tcp.Primitive.Request;

import java.nio.ByteBuffer;

public record FusionMergeRequest(String name) implements Request {

    @Override
    public void fillBuffer(ByteBuffer buffer) {
        buffer.put((byte)15);
        encode(buffer,100,name);
    }
}
