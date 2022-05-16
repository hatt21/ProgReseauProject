package fr.upem.net.tcp.Client;

import fr.upem.net.tcp.Primitive.Reader;
import fr.upem.net.tcp.Primitive.Request;
import fr.upem.net.tcp.Reader.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.logging.Logger;

public class ContextClient {
    private static final Logger logger = Logger.getLogger(ContextClient.class.getName());
    private static final int BUFFER_SIZE = 10000;
    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    private final ArrayDeque<Request> queue = new ArrayDeque<>();
    private boolean closed = false;
    public String serverName;
    private ClientChat client;

    public ContextClient(SelectionKey key,ClientChat client) {
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
                    System.out.println(request.login() + " from server " + request.serverName() + " sent " + request.msg());
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
                    System.out.println(request.loginSrc() + " from server " + request.serverSrc() + " sent " + request.msg());
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
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var request = reader.get();
                    fill(request.block(),request.filename());
                    System.out.println(request.loginSrc() + " from server " + request.serverSrc() + " sent a file named: " + request.filename());
                    reader.reset();
                    return;
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
            else{
                return;
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

