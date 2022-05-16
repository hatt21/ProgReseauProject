package fr.upem.net.tcp.Server;

import fr.upem.net.tcp.Primitive.Request;
import fr.upem.net.tcp.Request.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChaton {


    private static final int BUFFER_SIZE = 10000;
    private static final Logger logger = Logger.getLogger(ServerChaton.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final SocketChannel sc;
    private final Selector selector;
    public final String serverName;
    private final HashMap<SocketChannel, String> clients = new HashMap<>();
    public final HashMap<ServerSocketChannel, String> megaServer = new HashMap<>();
    private final ArrayDeque<Request> cmds = new ArrayDeque<>();
    private ServerContext communicationWithLeader;
    public InetSocketAddress inetSocketAddress;
    public boolean isLeader = true;

    public ServerChaton(int port, String serverName) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        this.inetSocketAddress = new InetSocketAddress(port);
        serverSocketChannel.bind(inetSocketAddress);
        selector = Selector.open();
        this.serverName=serverName;
        this.sc = SocketChannel.open();
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        Thread console = new Thread(this::consoleRun);
        console.start();

        while (!Thread.interrupted()) {
            //Helpers.printKeys(selector); // for debug
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    public boolean checkLogIn(SocketChannel sc, String login){
        if (clients.containsValue(login)){
            return false;
        }
        else{
            clients.put(sc,login);
        }
        return true;
    }

    public boolean SendPrivateFile(FileRequest request) throws IOException {
        for (var entry : clients.entrySet()) {
            if (entry.getValue().equals(request.loginDst())) {
                var key = entry.getKey().keyFor(selector);
                var ctx = (ServerContext) key.attachment();
                ctx.sendFile(request);
                return true;
            }
        }
        return false;
    }

    public boolean SendPrivateMessage(MessagePrivRequest request){
        for (var entry : clients.entrySet()) {
            if (entry.getValue().equals(request.loginDst())) {
                var key = entry.getKey().keyFor(selector);
                var ctx = (ServerContext) key.attachment();
                ctx.queueRequest(request);
                return true;
            }
        }
        return false;
    }

    private void treatKey(SelectionKey key) {
        //Helpers.printSelectedKey(key); // for debug

        try {
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
        try {
            if (key.isValid() && key.isConnectable()) {
                ((ServerContext) key.attachment()).doConnect();
            }
            if (key.isValid() && key.isWritable()) {
                ((ServerContext) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((ServerContext) key.attachment()).doRead();
            }
        } catch (IOException e) {
            logger.log(Level.INFO, "Connection closed with client due to IOException");
            silentlyClose(key);
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        var ssc = (ServerSocketChannel) key.channel();
        var client = ssc.accept();
        if (client == null) {
            logger.warning("accept() returned null");
            return;
        }
        client.configureBlocking(false);
        var skey = client.register(selector, SelectionKey.OP_READ);
        skey.attach(new ServerContext(skey, this));
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    public void broadcast(Request request) {
        selector.keys().forEach(key -> {
            if (key.channel() == serverSocketChannel) {
                return;
            }
            var ctx = (ServerContext) key.attachment();
            if (ctx.login!=null && !ctx.closed){
                ctx.queueRequest(request);
            }
        });
    }

    private void consoleRun() {
        try {
            try (var scanner = new Scanner(System.in)) {
                while (!Thread.interrupted() && scanner.hasNextLine()) {
                    var msg = scanner.nextLine();
                    sendCommand(msg);
                }
            }
            logger.info("Console thread stopping");
        } catch (InterruptedException | IOException e) {
            logger.info("Console thread has been interrupted");
        }
    }

    private void sendCommand(String msg) throws InterruptedException, IOException {
        if (StandardCharsets.UTF_8.encode(msg).remaining() > BUFFER_SIZE) {
            logger.warning("Message too long");
            return;
        }
        synchronized (cmds) {
            var message = msg.split(" ");
            if (message.length != 3) {
                return;
            }
            if (message[0].equals("FUSION")){
                if (isLeader){
                    launchFusion(message[1],Integer.parseInt(message[2]));
                }
                else{
                    sendLeader(message[1],Integer.parseInt(message[2]));
                }
            }
            selector.wakeup();
        }
    }

    public void launchFusion(String ipaddress, int port) throws IOException {
        sc.configureBlocking(false);
        var key = sc.register(selector, SelectionKey.OP_CONNECT);
        communicationWithLeader = new ServerContext(key,this);
        key.attach(communicationWithLeader);
        sc.connect(new InetSocketAddress(ipaddress,port));
    }

    private void sendLeader(String ipaddress, int port){
        var inetSocketAddress = new InetSocketAddress(ipaddress,port);
        var ipaddressBis = inetSocketAddress.getAddress().getAddress();
        var IpAddress = ByteBuffer.wrap(ipaddressBis);
        var version = ipaddressBis.length == 4 ? (byte)4 :(byte)6;
        communicationWithLeader.queueRequest(new FusionRequ12Request(version,IpAddress,port));
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 2) {
            usage();
            return;
        }

        if (args[1].getBytes().length > 96*Byte.BYTES){
            System.out.println("Server name is too big");
            return;
        }

        new ServerChaton(Integer.parseInt(args[0]),args[1]).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerChaton port name");
    }
}