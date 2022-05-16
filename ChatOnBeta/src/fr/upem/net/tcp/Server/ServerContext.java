package fr.upem.net.tcp.Server;

import fr.upem.net.tcp.Primitive.ByteReader;
import fr.upem.net.tcp.Primitive.Reader;
import fr.upem.net.tcp.Primitive.Request;
import fr.upem.net.tcp.Reader.*;
import fr.upem.net.tcp.Request.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.logging.Logger;

public class ServerContext {
    private static final int BUFFER_SIZE = 10000;
    private static final Logger logger = Logger.getLogger(ServerContext.class.getName());
    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    private final ArrayDeque<Request> queue = new ArrayDeque<>();
    public boolean closed = false;
    private final ServerChaton server;
    public String login;
    private String password;
    public boolean isFusionning = true;
    private InetSocketAddress leader;

    public ServerContext(SelectionKey key, ServerChaton server) {
        this.key = key;
        this.sc = (SocketChannel) key.channel();
        this.server = server;
    }

    private void LoginAnonymous(){
        var reader = new LogAnoRequestReader();
        for (; ; ) {
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var request = reader.get();
                    this.login = request.login();
                    if (server.checkLogIn(sc,login)){
                        queueRequest(new LogAcceptRequest(server.serverName));
                    }
                    else{
                        queueRequest(new LogRefusedRequest());
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

    private void LoginPassword(){
        var reader = new LogPassRequestReader();
        for (; ; ) {
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var request = reader.get();
                    this.login = request.login();
                    this.password = request.password();
                    if (server.checkLogIn(sc,login)){
                        queueRequest(new LogAcceptRequest(server.serverName));
                    }
                    else{
                        queueRequest(new LogRefusedRequest());
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

    private void PrivateMessage(){
        var reader = new MessagePrivReader();
        for (; ; ) {
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var message = reader.get();
                    if (!server.SendPrivateMessage(message)){
                        logger.info("client not found");
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

    private void PublicMessage(){
        var reader = new MessagePubReader();
        for (; ; ) {
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var message = reader.get();
                    server.broadcast(message);
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

    private void PrivateFile() throws IOException {
        var reader = new FileRequestReader();
        for (; ; ) {
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {

                case DONE:
                    var file = reader.get();
                    if (!server.SendPrivateFile(file)){
                        logger.info("client not found");
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

    private void FusionInit() throws IOException {
        var reader = new FusionInitReader();
        for (; ; ) {
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var request = reader.get();
                    if (server.isLeader){
                        var names = request.namesMembers();
                        var boucle = true;
                        for (int i =0; boucle && i< names.size();i++){
                            if (server.megaServer.containsValue(names.get(i))){
                                queueRequest(new FusionKoRequest());
                                boucle=false;
                            }
                        }
                        if (boucle){
                            sendFusionOk();
                        }
                    }
                    else{
                        sendFusionFwd();
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


    private void FusionOk(){
        var reader = new FusionOkReader();
        for (; ; ) {
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var request = reader.get();
                    if (server.serverName.compareTo(request.name())<0){
                        //A<B
                    }
                    else{
                        //B<A
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

    private void FusionFwd(){
        var reader = new FusionFwdReader();
        for (; ; ) {
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var request = reader.get();
                    try {
                        server.launchFusion(StandardCharsets.UTF_8.decode(request.IpAddress()).toString(),request.port());
                    } catch (IOException e) {
                        return;
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

    private void Fusion12() throws IOException {
        var reader = new FusionRequ12Reader();
        for (; ; ) {
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    if (isFusionning){
                        queueRequest(new FusionRequ13Request((byte)0));
                    }
                    var request = reader.get();
                    server.launchFusion(request.toString(),request.port());
                    queueRequest(new FusionRequ13Request((byte)1));
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

    private void Fusion13(){
        var reader = new ByteReader();
        for (; ; ) {
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var state = reader.get();
                    if (state== (byte)0){
                        logger.info("La fusion n'est pas possible");
                    }
                    else if (state == (byte)1){
                        logger.info("La fusion a été initiée");
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

    private void processIn() throws IOException {
        var reader = new OpCodeReader();
        for (; ; ) {
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var opCode = reader.get();
                    switch (opCode) {
                        case 0:
                            LoginAnonymous();
                            break;
                        case 1:
                            LoginPassword();
                            break;
                        case 4:
                            PublicMessage();
                            break;
                        case 5:
                            PrivateMessage();
                            break;
                        case 6:
                            PrivateFile();
                            break;
                        case 8:
                            FusionInit();
                            break;
                        case 9:
                            FusionOk();
                            break;
                        case 10:
                            logger.info("Fusion not possible");
                            silentlyClose();
                            break;
                        case 11:
                            FusionFwd();
                            silentlyClose();
                        case 12:
                            Fusion12();
                            break;
                        case 13:
                            Fusion13();
                            silentlyClose();
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

    public void sendFile(FileRequest request) throws IOException {
        request.fillBuffer(bufferOut);
        doWrite();
    }

    private void sendFusionFwd() throws IOException {
        var ipaddress = leader.getAddress().getAddress();
        var IpAddress = ByteBuffer.wrap(ipaddress);
        var port = leader.getPort();
        var version = ipaddress.length == 4 ? (byte)4 :(byte)6;

        var request = new FusionFwdRequest(version,IpAddress, port);
        request.fillBuffer(bufferOut);
        doWrite();
    }


    private void sendFusionAsk() throws IOException {
        var ipaddress = server.inetSocketAddress.getAddress().getAddress();
        var IpAddress = ByteBuffer.wrap(ipaddress);
        var port = server.inetSocketAddress.getPort();
        var version = ipaddress.length == 4 ? (byte)4 :(byte)6;
        ArrayList<String> names = new ArrayList<>();
        server.megaServer.forEach((k,v)->names.add(v));

        var request = new FusionInitRequest(server.serverName,version,IpAddress, port, names.size(),names);
        request.fillBuffer(bufferOut);
        doWrite();
    }

    private void sendFusionOk() throws IOException {
        var ipaddress = server.inetSocketAddress.getAddress().getAddress();
        var IpAddress = ByteBuffer.wrap(ipaddress);
        var port = server.inetSocketAddress.getPort();
        var version = ipaddress.length == 4 ? (byte)4 :(byte)6;
        ArrayList<String> names = new ArrayList<>();
        server.megaServer.forEach((k,v)->names.add(v));

        var request = new FusionOkRequest(server.serverName,version,IpAddress, port, names.size(),names);
        request.fillBuffer(bufferOut);
        doWrite();
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

    public void doConnect() throws IOException {
        if (!sc.finishConnect()) {
            logger.warning("Selector lied");
            return;
        }
        sendFusionAsk();
        key.interestOps(SelectionKey.OP_WRITE);
    }
}