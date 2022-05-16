package fr.upem.net.tcp.Reader;

import fr.upem.net.tcp.Request.LogPassRequest;
import fr.upem.net.tcp.Primitive.Reader;
import fr.upem.net.tcp.Primitive.StringReader;

import java.nio.ByteBuffer;

public class LogPassRequestReader implements Reader<LogPassRequest> {

    private enum State { DONE, WAITING, ERROR }

    private final StringReader reader = new StringReader();
    private State state = State.WAITING;
    private String login;
    private String password;

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

        status = reader.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        password = reader.get();
        reader.reset();

        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public LogPassRequest get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new LogPassRequest(login,password);
    }

    @Override
    public void reset() {
        state = State.WAITING;
        reader.reset();
        password=null;
        login = null;
    }
}