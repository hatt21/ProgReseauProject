package fr.upem.net.tcp.Reader;

import fr.upem.net.tcp.Primitive.Reader;
import fr.upem.net.tcp.Primitive.StringReader;
import fr.upem.net.tcp.Request.MessagePubRequest;

import java.nio.ByteBuffer;

public class MessagePubReader implements Reader<MessagePubRequest> {

    private enum State { DONE, WAITING, ERROR }
    private final StringReader reader = new StringReader();
    private State state = State.WAITING;
    private String serverName;
    private String login;
    private String message;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        var status = reader.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }

        serverName = reader.get();
        reader.reset();

        status = reader.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        login= reader.get();
        reader.reset();

        status = reader.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        message = reader.get();
        reader.reset();

        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public MessagePubRequest get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new MessagePubRequest(serverName, login, message);
    }

    @Override
    public void reset() {
        state = State.WAITING;
        reader.reset();
        serverName = null;
        login=null;
        message=null;
    }
}