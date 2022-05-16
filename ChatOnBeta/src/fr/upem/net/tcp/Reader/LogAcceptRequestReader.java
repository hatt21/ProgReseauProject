package fr.upem.net.tcp.Reader;

import fr.upem.net.tcp.Primitive.Reader;
import fr.upem.net.tcp.Primitive.StringReader;

import java.nio.ByteBuffer;


public class LogAcceptRequestReader implements Reader<String> {

    private enum State { DONE, WAITING, ERROR }
    private final StringReader reader = new StringReader();
    private State state = State.WAITING;
    private String serverName;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state== State.DONE || state == State.ERROR){
            throw new IllegalStateException();
        }

        var status = reader.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        serverName = reader.get();
        reader.reset();

        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public String get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return serverName;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        reader.reset();
        serverName = null;
    }
}