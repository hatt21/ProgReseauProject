package fr.upem.net.tcp.Reader;

import fr.upem.net.tcp.Primitive.Reader;
import fr.upem.net.tcp.Primitive.StringReader;
import fr.upem.net.tcp.Request.FusionMergeRequest;

import java.nio.ByteBuffer;

public class FusionMergeReader implements Reader<FusionMergeRequest> {

    private enum State { DONE, WAITING, ERROR }
    private State state = State.WAITING;
    private String name;
    private ProcessStatus status;
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        var readerStr = new StringReader();

        if (state== State.DONE || state == State.ERROR){
            throw new IllegalStateException();
        }

        status = readerStr.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        name = readerStr.get();
        readerStr.reset();

        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public FusionMergeRequest get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new FusionMergeRequest(name);
    }

    @Override
    public void reset() {
        state=State.WAITING;
        name=null;
    }
}
