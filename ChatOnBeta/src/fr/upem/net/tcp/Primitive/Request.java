package fr.upem.net.tcp.Primitive;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public interface Request {

    Logger logger = Logger.getLogger(Request.class.getName());

    void fillBuffer(ByteBuffer buffer);

    default void encode(ByteBuffer buffer, int size, String str){
        var tmp = StandardCharsets.UTF_8.encode(str);
        var length = tmp.remaining();

        if (buffer.remaining()< Integer.BYTES + length || length > size){
            logger.info("Server name is too big");
            return;
        }

        buffer.putInt(length).put(tmp);
    }
}
