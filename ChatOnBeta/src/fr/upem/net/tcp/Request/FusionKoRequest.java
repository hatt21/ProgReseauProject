package fr.upem.net.tcp.Request;

import fr.upem.net.tcp.Primitive.Request;

import java.nio.ByteBuffer;

public class FusionKoRequest implements Request {
    @Override
    public void fillBuffer(ByteBuffer buffer) {
        buffer.put((byte)10);
    }
}
