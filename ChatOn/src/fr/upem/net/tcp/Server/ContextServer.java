package fr.upem.net.tcp.Server;

import fr.upem.net.tcp.Primitive.Reader;
import fr.upem.net.tcp.Primitive.Request;
import fr.upem.net.tcp.Reader.*;
import fr.upem.net.tcp.Request.FileRequest;
import fr.upem.net.tcp.Request.LogAcceptRequest;
import fr.upem.net.tcp.Request.LogRefusedRequest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.logging.Logger;

public class ContextServer {
	private static final Logger logger = Logger.getLogger(ContextServer.class.getName());
	private static final int BUFFER_SIZE = 10000;
	private final SelectionKey key;
	private final SocketChannel sc;
	private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
	private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
	private final ArrayDeque<Request> queue = new ArrayDeque<>();
	public boolean closed = false;
	private final ServerChaton server;
	public String login;
	private String password;

	public ContextServer(SelectionKey key, ServerChaton server) {
		this.key = key;
		this.sc = (SocketChannel) key.channel();
		this.server = server;
	}

	private void LoginAnonymous() {
		var reader = new LogAnoRequestReader();

		Reader.ProcessStatus status = reader.process(bufferIn);
		switch (status) {
		case DONE:
			var request = reader.get();
			this.login = request.login();
			if (server.checkLogIn(sc, login)) {
				queueRequest(new LogAcceptRequest(server.serverName));
			} else {
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

	private void LoginPassword() {
		var reader = new LogPassRequestReader();

		Reader.ProcessStatus status = reader.process(bufferIn);
		switch (status) {
		case DONE:
			var request = reader.get();
			this.login = request.login();
			this.password = request.password();
			if (server.checkLogIn(sc, login)) {
				queueRequest(new LogAcceptRequest(server.serverName));
			} else {
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

	private void PrivateMessage() {
		var reader = new MessagePrivReader();

		Reader.ProcessStatus status = reader.process(bufferIn);
		switch (status) {
		case DONE:
			var message = reader.get();
			if (!server.SendPrivateMessage(message)) {
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

	private void PublicMessage() {
		var reader = new MessagePubReader();

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

	private void PrivateFile() throws IOException {
		var reader = new FileRequestReader();

		Reader.ProcessStatus status = reader.process(bufferIn);
		switch (status) {
		case DONE:
			var file = reader.get();
			if (!server.SendPrivateFile(file)) {
				logger.info("client not found");
			}
			reader.reset();
			return;
		case REFILL:
			return;
		case ERROR:
			silentlyClose();
			return;
		}

	}

	private void processIn() throws IOException {
		var reader = new OpCodeReader();
		for (;;) {
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
		while (!queue.isEmpty() && bufferOut.hasRemaining()) {
			var currentBuffer = ByteBuffer.allocate(BUFFER_SIZE);
			var msg = queue.peek();
			msg.fillBuffer(currentBuffer);
			if (bufferOut.remaining() > currentBuffer.flip().remaining()) {
				bufferOut.put(currentBuffer);
				queue.pop();
			} else {
				return;
			}
		}
	}

	public void sendFile(FileRequest request) throws IOException {
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
}