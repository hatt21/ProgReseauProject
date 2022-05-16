package fr.upem.net.tcp.Primitive;

import java.nio.ByteBuffer;

public interface Reader<T> {

    enum ProcessStatus { DONE, REFILL, ERROR }

    ProcessStatus process(ByteBuffer buffer);

    T get();

    void reset();
}