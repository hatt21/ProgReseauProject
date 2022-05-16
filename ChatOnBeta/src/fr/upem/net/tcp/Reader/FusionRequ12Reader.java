package fr.upem.net.tcp.Reader;

import fr.upem.net.tcp.Primitive.ByteReader;
import fr.upem.net.tcp.Primitive.IntReader;
import fr.upem.net.tcp.Primitive.Reader;
import fr.upem.net.tcp.Request.FusionRequ12Request;

import java.nio.ByteBuffer;

public class FusionRequ12Reader implements Reader<FusionRequ12Request> {

    private enum State { DONE, WAITING, ERROR }
    private State state = State.WAITING;
    private Byte version;
    private ByteBuffer IpAddress;
    private int port;
    private ProcessStatus status;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        var readerInt = new IntReader();
        var readerByte = new ByteReader();

        if (state== State.DONE || state == State.ERROR){
            throw new IllegalStateException();
        }

        status = readerByte.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        version = readerByte.get();
        readerByte.reset();

        IpAddress = ByteBuffer.allocate((int)version*Byte.BYTES);
        for (int i =0; i< (int)version; i++){
            status = readerByte.process(buffer);
            if (status != ProcessStatus.DONE){
                return status;
            }
            var byt = readerByte.get();
            IpAddress.put(byt);
            readerByte.reset();
        }
        IpAddress.flip();

        status = readerInt.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        port = readerInt.get();
        readerInt.reset();


        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public FusionRequ12Request get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new FusionRequ12Request(version,IpAddress,port);
    }

    @Override
    public void reset() {
        state = State.WAITING;
        version = null;
        IpAddress = null;
        port = 0;
    }
}
