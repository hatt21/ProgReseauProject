package fr.upem.net.tcp.Request;

import fr.upem.net.tcp.Primitive.Request;

import java.nio.ByteBuffer;
import java.util.Objects;

public record FileRequest(String serverSrc, String loginSrc, String serverDst, String loginDst, String filename, int nbBlocks, int blockSize, ByteBuffer block) implements Request {

    @Override
    public void fillBuffer(ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        buffer.put((byte) 6);
        encode(buffer,100,serverSrc);
        encode(buffer,30,loginSrc);
        encode(buffer,100,serverDst);
        encode(buffer,30,loginDst);
        encode(buffer,100,filename);
        buffer.putInt(nbBlocks).putInt(blockSize);
        buffer.put(block);
    }
}
