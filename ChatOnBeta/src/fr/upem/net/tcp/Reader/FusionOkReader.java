package fr.upem.net.tcp.Reader;

import fr.upem.net.tcp.Primitive.ByteReader;
import fr.upem.net.tcp.Primitive.IntReader;
import fr.upem.net.tcp.Primitive.Reader;
import fr.upem.net.tcp.Primitive.StringReader;
import fr.upem.net.tcp.Request.FusionOkRequest;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class FusionOkReader implements Reader<FusionOkRequest> {
    private enum State { DONE, WAITING, ERROR }
    private State state = State.WAITING;
    private String name;
    private Byte version;
    private ByteBuffer IpAddress;
    private int port;
    private int nbMembers = 0;
    private ArrayList<String> namesMembers = new ArrayList<>();
    private ProcessStatus status;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        var readerStr = new StringReader();
        var readerInt = new IntReader();
        var readerByte = new ByteReader();

        if (state== State.DONE || state == State.ERROR){
            throw new IllegalStateException();
        }

        status = readerStr.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        name = readerStr.get();
        readerStr.reset();

        status = readerByte.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        version = readerByte.get();
        readerStr.reset();

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

        status = readerInt.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        nbMembers = readerInt.get();
        readerInt.reset();

        for (int i =0; i< nbMembers; i++){
            status = readerStr.process(buffer);
            if (status != ProcessStatus.DONE){
                return status;
            }
            namesMembers.add(readerStr.get());
            readerStr.reset();
        }

        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public FusionOkRequest get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new FusionOkRequest( name, version,IpAddress,port, nbMembers, namesMembers);
    }

    @Override
    public void reset() {
        state = State.WAITING;
        name = null;
        version = null;
        IpAddress = null;
        port = 0;
        namesMembers.clear();
    }
}
