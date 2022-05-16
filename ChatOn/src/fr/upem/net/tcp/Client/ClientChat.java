package fr.upem.net.tcp.Client;

import fr.upem.net.tcp.Primitive.Request;
import fr.upem.net.tcp.Request.FileRequest;
import fr.upem.net.tcp.Request.MessagePrivRequest;
import fr.upem.net.tcp.Request.MessagePubRequest;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientChat {

    private final static int BUFFER_SIZE = 10_000;
    private final static Logger logger = Logger.getLogger(ClientChat.class.getName());
    private final SocketChannel sc;
    private final Selector selector;
    private final InetSocketAddress serverAddress;
    private final String login;
    private ContextClient ContextClient;
    private final ArrayDeque<Request> cmds = new ArrayDeque<>();
    public String path;

    public ClientChat(String login, InetSocketAddress serverAddress, String path) throws IOException {
        this.serverAddress = serverAddress;
        this.login = login;
        this.sc = SocketChannel.open();
        this.selector = Selector.open();
        this.path=path;
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
            if (msg.length() <= 0) {
                return;
            }
            switch (msg.charAt(0)) {
                case '/':
                    if (msg.matches("/.*:.*")) {
                        var tab1 = msg.split(":");
                        var tab2 = tab1[1].split(" ", 2);
                        String pathFile = path+"/"+tab2[1];
                        byte[] bytes = Files.readAllBytes(Paths.get(pathFile));
                        var length = bytes.length;
                        int nbBlock = 0;
                        for (int i = 0; i<length; i+=5000){
                            nbBlock++;
                        }

                        if (length>5000){
                            for (int i = 0; i< nbBlock; i++){
                                var rest = length - (i*5000);
                                var blockSize =  rest < 5000 ? rest : 5000;
                                var position = i*5000;

                                var bufferBis = ByteBuffer.allocate(blockSize);
                                for (int j=0; j<blockSize;j++){
                                    bufferBis.put(bytes[position+j]);
                                }
                                bufferBis.flip();
                                ContextClient.queueRequest(new FileRequest(ContextClient.serverName, login,tab2[0],tab1[0].replace("/",""),tab2[1],nbBlock,blockSize, bufferBis));
                            }
                        }
                        else{
                            var bufferBis = ByteBuffer.allocate(length);
                            bufferBis.put(bytes);
                            bufferBis.flip();
                            ContextClient.queueRequest(new FileRequest(ContextClient.serverName, login,tab2[0],tab1[0].replace("/",""),tab2[1],nbBlock,length,bufferBis));
                        }
                    }
                    break;
                case '@':
                    if (!msg.matches("@.*:.*")) {
                        break;
                    }
                    var tab1 = msg.split(":");
                    var tab2 = tab1[1].split(" ", 2);
                    cmds.addLast(new MessagePrivRequest(ContextClient.serverName,login,tab2[0],tab1[0].replace("@",""),tab2[1]));
                    break;
                default:
                    cmds.addLast(new MessagePubRequest(ContextClient.serverName,login, msg));
            }
            selector.wakeup();
        }
    }

    private void processCommands() {
        synchronized (cmds) {
            while (!cmds.isEmpty())
                ContextClient.queueRequest(cmds.poll());
        }
    }

    public void launch() throws IOException {
        sc.configureBlocking(false);
        var key = sc.register(selector, SelectionKey.OP_CONNECT);
        ContextClient = new ContextClient(key,this);
        key.attach(ContextClient);
        sc.connect(serverAddress);

        Thread console = new Thread(this::consoleRun);
        console.start();

        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
                processCommands();
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    public void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isConnectable()) {
                ContextClient.doConnect(login);
            }
            if (key.isValid() && key.isWritable()) {
                ContextClient.doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ContextClient.doRead();
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
    }

    private void silentlyClose(SelectionKey key) {
        var sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 4) {
            usage();
            return;
        }
        new ClientChat(args[0], new InetSocketAddress(args[1], Integer.parseInt(args[2])),args[3]).launch();
    }

    private static void usage() {
        System.out.println("Usage : ClientChat login hostname port");
    }
}