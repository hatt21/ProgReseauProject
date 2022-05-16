package fr.upem.net.tcp.Reader;

import fr.upem.net.tcp.Primitive.ByteReader;
import fr.upem.net.tcp.Primitive.IntReader;
import fr.upem.net.tcp.Primitive.Reader;
import fr.upem.net.tcp.Primitive.StringReader;
import fr.upem.net.tcp.Request.FileRequest;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FileRequestReader implements Reader<FileRequest> {

    private enum State { DONE, WAITING, ERROR }
    private State state = State.WAITING;
    private String serverSrc;
    private String loginSrc;
    private String serverDst;
    private String loginDst;
    private String filename;
    private Integer nbBlock;
    private Integer blockSize;
    private ByteBuffer block;
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
        serverSrc = readerStr.get();
        readerStr.reset();

        status = readerStr.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        loginSrc = readerStr.get();
        readerStr.reset();

        status = readerStr.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        serverDst = readerStr.get();
        readerStr.reset();

        status = readerStr.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        loginDst = readerStr.get();
        readerStr.reset();

        status = readerStr.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        filename = readerStr.get();
        readerStr.reset();

        status = readerInt.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        nbBlock = readerInt.get();
        readerInt.reset();

        status = readerInt.process(buffer);
        if (status != ProcessStatus.DONE){
            return status;
        }
        blockSize = readerInt.get();
        readerInt.reset();

        block = ByteBuffer.allocate(blockSize*Byte.BYTES);
        for (int i =0; i< blockSize; i++){
            status = readerByte.process(buffer);
            if (status != ProcessStatus.DONE){
                return status;
            }
            var byt = readerByte.get();
            block.put(byt);
            readerByte.reset();
        }
        block.flip();

        state = State.DONE;
        return ProcessStatus.DONE;
    }

    public void fill() {
        try(RandomAccessFile aFile = new RandomAccessFile("../../../src/fr/upem/net/tcp/fileReceived/"+filename, "rw");
            FileChannel inChannel = aFile.getChannel();) {
            inChannel.write(block);
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public FileRequest get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new FileRequest( serverSrc, loginSrc, serverDst, loginDst, filename, nbBlock, blockSize, block);
    }

    @Override
    public void reset() {
        state = State.WAITING;
        serverSrc = null;
        loginSrc  = null;
        serverDst = null;
        loginDst = null;
        filename  = null;
        nbBlock = null;
        blockSize = null;
        block = null;
    }
}
