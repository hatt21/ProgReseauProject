package fr.upem.net.tcp.Request;

import fr.upem.net.tcp.Primitive.Request;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public record FusionInitRequest(String name,Byte version,ByteBuffer IpAddress,int port, int nbMembers, ArrayList<String> namesMembers) implements Request {

    @Override
    public void fillBuffer(ByteBuffer buffer) {
        buffer.put((byte) 8);
        encode(buffer,100,name);
        buffer.put(version).put(IpAddress).putInt(port);
        buffer.putInt(nbMembers);
        namesMembers.forEach(name -> encode(buffer,100,name));
    }
}
