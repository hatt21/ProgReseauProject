package fr.upem.net.tcp.Request;

import fr.upem.net.tcp.Primitive.Request;

import java.nio.ByteBuffer;

public record MessagePrivRequest(String serverSrc, String loginSrc, String serverDst, String loginDst, String msg) implements Request {

    @Override
    public void fillBuffer(ByteBuffer buffer) {
        buffer.put((byte) 5);
        encode(buffer,100, serverSrc);
        encode(buffer,100, loginSrc);
        encode(buffer,100, serverDst);
        encode(buffer,100, loginDst);
        encode(buffer,1024, msg);
    }
}
