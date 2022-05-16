package fr.upem.net.tcp.Server;

import fr.upem.net.tcp.Primitive.Request;
import fr.upem.net.tcp.Request.FileRequest;
import fr.upem.net.tcp.Request.MessagePrivRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChaton {

    private static final Logger logger = Logger.getLogger(ServerChaton.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    public final String serverName;
    private final HashMap<SocketChannel, String> clients = new HashMap<>();

    public ServerChaton(int port, String serverName) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        this.serverName=serverName;
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

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
                var ctx = (ContextServer) key.attachment();
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
                var ctx = (ContextServer) key.attachment();
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
            if (key.isValid() && key.isWritable()) {
                ((ContextServer) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((ContextServer) key.attachment()).doRead();
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
        skey.attach(new ContextServer(skey, this));
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
            var ctx = (ContextServer) key.attachment();
            if (ctx.login!=null && !ctx.closed){
                ctx.queueRequest(request);
            }
        });
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

        if (Integer.parseInt(args[0])<0 || Integer.parseInt(args[0])>65535){
            System.out.println("Port is incorrect");
            return;
        }

        new ServerChaton(Integer.parseInt(args[0]),args[1]).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerChaton port name");
    }
}