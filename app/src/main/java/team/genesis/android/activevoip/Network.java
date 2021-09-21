package team.genesis.android.activevoip;

import io.netty.buffer.ByteBuf;

public class Network {
    public static byte[] readNbytes(ByteBuf buf,int maxBytes)throws IndexOutOfBoundsException{
        int length = buf.readInt();
        if(length<0||length>maxBytes)   throw new IndexOutOfBoundsException();
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return bytes;
    }
    public static void writeBytes(ByteBuf buf,byte[] bytes){
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }
}
