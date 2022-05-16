package fr.upem.net.tcp.Reader;

import fr.upem.net.tcp.Primitive.Reader;
import fr.upem.net.tcp.Primitive.StringReader;
import fr.upem.net.tcp.Request.LogAnoRequest;

import java.nio.ByteBuffer;

public class LogAnoRequestReader implements Reader<LogAnoRequest> {

    private enum State { DONE, WAITING, ERROR }
    private final StringReader reader = new StringReader();
    private State state = State.WAITING;
    private String login;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state== State.DONE || state == State.ERROR){
            throw new IllegalStateException();
        }

        var status = reader.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        login = reader.get();
        reader.reset();

        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public LogAnoRequest get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new LogAnoRequest(login);
    }

    @Override
    public void reset() {
        state = State.WAITING;
        reader.reset();
        login = null;
    }
}