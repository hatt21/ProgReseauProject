package fr.upem.net.tcp.Reader;

import fr.upem.net.tcp.Primitive.Reader;
import fr.upem.net.tcp.Primitive.StringReader;
import fr.upem.net.tcp.Request.MessagePrivRequest;

import java.nio.ByteBuffer;

public class MessagePrivReader implements Reader<MessagePrivRequest> {

    private enum State { DONE, WAITING,ERROR }

    private final StringReader reader = new StringReader();
    private State state = State.WAITING;
    private String serverNameSRC;
    private String loginSRC;
    private String serverNameDST;
    private String loginDST;
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
        serverNameSRC = reader.get();
        reader.reset();

        status = reader.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        loginSRC= reader.get();
        reader.reset();

        status = reader.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        serverNameDST= reader.get();
        reader.reset();

        status = reader.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        loginDST= reader.get();
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
    public MessagePrivRequest get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new MessagePrivRequest(serverNameSRC,loginSRC,serverNameDST,loginDST,message);
    }

    @Override
    public void reset() {
        state = State.WAITING;
        reader.reset();
        serverNameSRC = null;
        loginSRC=null;
        serverNameDST = null;
        loginDST =null;
        message=null;
    }
}