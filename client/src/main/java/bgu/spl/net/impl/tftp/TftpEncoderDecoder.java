package bgu.spl.net.impl.tftp;
import bgu.spl.net.api.MessageEncoderDecoder;
import java.util.List;
import java.util.ArrayList;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private short op = -1; // -1 means we never read anything yet
    private List<Byte> bytes = new ArrayList<Byte>();
    private short data_size = -1; // only for data packets

    @Override
    public byte[] decodeNextByte(byte nextByte) 
    {
        bytes.add(nextByte);
        switch (op)
        {
            case -1: // we read one byte
            {
                op = 0; 
                break;
            }
            case 0: // we read two bytes
            {
                op = twoByteToShort(new byte[] {0, nextByte});
                if (op == 10 || op == 6) // DISC or DIRQ
                {
                    return sendMsg();
                }
                break;
            }
            case 1: // RRQ
            case 2: // WRQ
            case 7: // LOGRQ
            case 8: // DELRQ
            {
                if (nextByte == 0)
                {
                    return sendMsg();
                }
                break;
            }
            
            case 5: // ERROR
            case 9: // BCAST
            {
                if (nextByte == 0 && bytes.size() > 4)
                {
                    return sendMsg();
                }
                break;
            }

            case 4: // ACK
            {
                if (bytes.size() == 4)
                {
                    return sendMsg();
                }
                break;
            }
            case 3: // DATA
            {
                if (bytes.size() == 4 && data_size == -1)
                {
                    data_size = twoByteToShort(new byte[] {bytes.get(2), bytes.get(3)});   
                }
                if (data_size != -1 && data_size == bytes.size() - 6 ) // first 6 bytes are not part of the data
                    {
                        data_size = -1;
                        return sendMsg();
                    }
                break;
            }
            default:
            {
                System.out.println("Not a clear Command: " + op);
                break;
            }
            
        }
 
        
        return null;
    }

    private byte[] sendMsg()
    {
        byte[] msg = new byte[bytes.size()];
        for (int i = 0; i < msg.length; i++)
        {
            msg[i] = bytes.get(i);
        }
        op = -1;
        bytes.clear();
        return msg;
    }

    public short twoByteToShort(byte[] bytes)
    {
        return ( short ) ((( short ) bytes [0]) << 8 | ( short ) ( bytes [1]) & 0xff );
        //return (short) ( (((short) (bytes[0] & 0xff)) << 8) | (short) (bytes[1]) & 0x00ff);

    }

    public byte[] shortToTwoBytes(short s) {
        return new byte[]{(byte)(s >> 8), (byte)s};
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }
}