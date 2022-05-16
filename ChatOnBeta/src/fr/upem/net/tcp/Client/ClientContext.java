package fr.upem.net.tcp.Client;

import fr.upem.net.tcp.Primitive.Reader;
import fr.upem.net.tcp.Primitive.Request;
import fr.upem.net.tcp.Reader.*;
import fr.upem.net.tcp.Request.FileRequest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.logging.Logger;

public class ClientContext {
    private final static Logger logger = Logger.getLogger(ClientContext.class.getName());
    private final static int BUFFER_SIZE = 10_000;
    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    private final ArrayDeque<Request> queue = new ArrayDeque<>();
    public boolean closed = false;
    public String serverName;
    private ClientChat client;

    public ClientContext(SelectionKey key,ClientChat client) {
        this.key = key;
        this.sc = (SocketChannel) key.channel();
        this.client=client;
    }

    private void loginAccepted(){
        logger.info("Connection OK");
        var reader = new LogAcceptRequestReader();
        for (; ; ) {
            fr.upem.net.tcp.Primitive.Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    this.serverName = reader.get();
                    reader.reset();
                    break;
                case REFILL:
                    return;
                case ERROR:
                    silentlyClose();
                    return;
            }
        }
    }

    private void publicMessage(){
        var reader = new MessagePubReader();
        for (; ; ) {
            fr.upem.net.tcp.Primitive.Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var request = reader.get();
                    System.out.println(request.login() + " from server: " + request.serverName() + " sent " + request.msg());
                    reader.reset();
                    break;
                case REFILL:
                    return;
                case ERROR:
                    silentlyClose();
                    return;
            }
        }
    }

    private void privateMessage(){
        var reader = new MessagePrivReader();
        for (; ; ) {
            fr.upem.net.tcp.Primitive.Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var request = reader.get();
                    System.out.println(request.loginSrc() + " from server: " + request.serverSrc() + " sent " + request.msg());
                    reader.reset();
                    break;
                case REFILL:
                    return;
                case ERROR:
                    silentlyClose();
                    return;
            }
        }
    }

    private void privateFile(){
        var reader = new FileRequestReader();
        for (; ; ) {
            fr.upem.net.tcp.Primitive.Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var request = reader.get();
                    fill(request.block(),request.filename());
                    System.out.println(request.loginSrc() + " from server: " + request.serverSrc() + " sent a file named: " + request.filename());
                    reader.reset();
                    break;
                case REFILL:
                    return;
                case ERROR:
                    silentlyClose();
                    return;
            }
        }
    }

    public void fill(ByteBuffer block,String filename){
        String path = client.path+"/"+filename.replace(".txt","Received.txt");
        try (FileOutputStream fos = new FileOutputStream(path,true)) {
            byte[] bytes = new byte[block.remaining()];
            block.get(bytes);
            fos.write(bytes);
        } catch (IOException e) {
            //
        }
    }

    private void processIn() {
        var reader = new OpCodeReader();
        for (; ; ) {
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var opCode = reader.get();
                    switch (opCode) {
                        case 2:
                            System.out.println("login accepted");
                            loginAccepted();
                            break;
                        case 3:
                            logger.info("Refused connection to server");
                            silentlyClose();
                            break;
                        case 4:
                            publicMessage();
                            break;
                        case 5:
                            privateMessage();
                            break;
                        case 6:
                            privateFile();
                            break;
                    }
                    reader.reset();
                    break;
                case REFILL:
                    return;
                case ERROR:
                    silentlyClose();
                    return;
            }
        }
    }

    public void queueRequest(Request request) {
        queue.add(request);
        processOut();
        updateInterestOps();
    }

    private void processOut() {
        while(!queue.isEmpty() && bufferOut.hasRemaining()) {
            var currentBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            var msg = queue.peek();
            msg.fillBuffer(currentBuffer);
            if (bufferOut.remaining()>currentBuffer.flip().remaining()){
                bufferOut.put(currentBuffer);
                queue.pop();
            }
        }
    }

    private void updateInterestOps() {
        int interestOps = 0;
        if (!closed && bufferIn.hasRemaining()) {
            interestOps |= SelectionKey.OP_READ;
        }
        if (bufferOut.position() != 0) {
            interestOps |= SelectionKey.OP_WRITE;
        }
        if (interestOps == 0) {
            silentlyClose();
            return;
        }
        key.interestOps(interestOps);
    }

    private void silentlyClose() {
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    private void sendLogIn(String login) throws IOException {
        bufferOut.clear();
        bufferOut.put( (byte) 0).putInt(login.length()).put(StandardCharsets.UTF_8.encode(login));
        doWrite();
    }

    public void sendFile(String serverNameSrc, String loginSrc, String loginDst, String serverNameDst, String fileName){
        File file = new File("file");
        try(RandomAccessFile aFile = new RandomAccessFile(file.getAbsolutePath().replace("/file","")+"/fr/upem/net/tcp/file/"+fileName, "r");
            FileChannel inChannel = aFile.getChannel();) {
            ByteBuffer buffer = ByteBuffer.allocate((int) inChannel.size());
            inChannel.read(buffer);
            buffer.flip();

            var length = buffer.remaining();
            int nbBlock = 0;
            for (int i = 0; i<length; i+=5000){
                nbBlock++;
            }

            if (length>5000){
                for (int i = 0; i< nbBlock; i++){
                    var rest = length - (i*5000);
                    var blockSize =  rest < 5000 ? rest : 5000;
                    var position = i*5000;
                    var oldLimit = buffer.limit();

                    buffer.position(position);
                    buffer.limit(position+blockSize);
                    var bufferBis = ByteBuffer.allocate(blockSize);
                    bufferBis.put(buffer);
                    buffer.limit(oldLimit);

                    bufferBis.flip();
                    var request = new FileRequest(serverNameSrc,loginSrc,serverNameDst,loginDst,fileName,nbBlock,blockSize, bufferBis);
                    request.fillBuffer(bufferOut);
                    doWrite();
                }
            }
            else{
                queueRequest(new FileRequest(serverNameSrc,loginSrc,serverNameDst,loginDst,fileName,nbBlock,length,buffer));
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void doRead() throws IOException {
        if (sc.read(bufferIn) == -1) {
            logger.info("Connection closed by " + sc.getRemoteAddress());
            closed = true;
        }
        processIn();
        updateInterestOps();
    }

    public void doWrite() throws IOException {
        bufferOut.flip();
        sc.write(bufferOut);
        bufferOut.compact();
        processOut();
        updateInterestOps();
    }

    public void doConnect(String login) throws IOException {
        if (!sc.finishConnect()) {
            logger.warning("Selector lied");
            return;
        }
        sendLogIn(login);
        key.interestOps(SelectionKey.OP_WRITE);
    }
}
