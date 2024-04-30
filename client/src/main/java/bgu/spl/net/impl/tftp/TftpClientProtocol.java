package bgu.spl.net.impl.tftp;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import bgu.spl.net.api.MessagingProtocol;
public class TftpClientProtocol implements MessagingProtocol<byte[]>
{

    
    
    /*
     * the client
     */
    private TftpClient client;

    /*
     * the data bytes received from the server in DIRQ 
     */
    List<Byte> DiRQ_Bytes;

    public TftpClientProtocol(TftpClient client)
    {
        this.client = client;
        DiRQ_Bytes = new LinkedList<>();
    }
    /**
     * process the given message 
     * @param msg the received message
     * @return the response to send or null if no response is expected by the client
     */
    public byte[] process(byte[] msg)
    {
        short op = client.getEncoderDecoder().twoByteToShort(new byte[] {msg[0], msg[1]});
        switch (op)
        {
            case 3: // DATA
            {
                return DATA(msg);
            }
            case 4: // ACK
            {
                return ACK(msg);
            }
            case 5: // ERROR
            {
                return ERROR(msg);
            }
            case 9: // BCAST
            {
                return BCAST(msg);
            }
            default:
            {
                return null;
            }
        }
    }

    /*
     * defines the behavior of the client when receiving an ack packet
     */
    public byte[] ACK(byte[] msg)
    {
        client.setError( (short) -1 ); // no error
        short block_num = client.getEncoderDecoder().twoByteToShort(new byte[] {msg[2], msg[3]});
        System.out.println("ACK "+ block_num);
        // wake up keyboard thread
        synchronized(client.getKeyboardThread())
        {
            client.getKeyboardThread().notifyAll();
        }
        return null;
    }
    
    /*
     * defines the behavior of the client when receiving a BCAST packet
     */
    public byte[] BCAST(byte[] msg)
    {
        if (client.getIsConnected()) // the BCAST should be sent only to connected clients
        {
            String del_or_add = (msg[2] == 0) ? "del" : "add";
            // cutting the opcode, the del/add byte and the last zero byte
            byte[] messageBytes = Arrays.copyOfRange(msg, 3, msg.length-1);
            // converting the message to a string by UTF-8 encoding
            String message = new String(messageBytes, StandardCharsets.UTF_8);
            System.out.println("BCAST " + del_or_add + " " + message);
        }
        return null;
    }

    /*
     * defines the behavior of the client when receiving an error packet
     */
    public byte[] ERROR(byte[] msg)
    {
        short errorCode = client.getEncoderDecoder().twoByteToShort(new byte[] {msg[2], msg[3]});
        client.setError(errorCode); // no need to synchronize since the keyboard thread is blocked during this method
        String errorMSG = new String(Arrays.copyOfRange(msg, 4, msg.length-1), StandardCharsets.UTF_8);
        System.out.println("ERROR " + errorCode +" " + errorMSG);
         // wake up keyboard thread
         synchronized(client.getKeyboardThread())
         {
            client.getKeyboardThread().notifyAll();
         }
        return null;
    }
    /*
     * defines the behavior of the client when receiving a data packet
     */
    public byte[] DATA(byte[] msg)
    {
        client.setError( (short) -1 );  // no error
        if (client.getLastRequestIsDIRQ()) // DIRQ
        {
            DIRQData(msg);    
        }
        else // RRQ
        {
            RRQData(msg);
        }
        // return ack with the block number
        return buildACKPacket(new byte[] {msg[4], msg[5]});
    }

    /**
     * @return true if the connection should be terminated
     */
    public boolean shouldTerminate()
    {
        return client.shouldTerminate();
    }

    /*
     * adds a new file to the list of files
     */
    private void addNewFile()
    {
        byte[] byteArray = new byte[DiRQ_Bytes.size()];
        for (int i = 0; i < DiRQ_Bytes.size(); i++) 
        {
            byteArray[i] = DiRQ_Bytes.get(i);
        }
        String fileName = new String(byteArray, StandardCharsets.UTF_8);
        client.getfilesNames().add(fileName);
        DiRQ_Bytes.clear();
    }



    /*
     * when receiving a DIRQ data packet we wait until all the data packets are received and we also convert the bytes to a list of file names so the keyboard thread can print them easily
     */
    private void DIRQData(byte[] msg)
    {
        // this loop converts the bytes in  DiRQ_Bytes to files names
        for (int i = 6; i < msg.length; i++) // the first 6 bytes are not a part of the data
        {
            // a zero byte indicates the end of a file name
            if (msg[i] == 0)
            {
                addNewFile();
                continue; // we don't want to add the zero byte to the list
            }
            DiRQ_Bytes.add(msg[i]);
        }

        if (msg.length < 518) // if the data is less than 518 bytes long, it is the last data packet
        {
            if (msg[msg.length - 1] != 0) // so we don't create a new empty file name
            {
                addNewFile(); // this is the last file name
            }
            // after creating the list of file names, we can wake up the keyboard thread
            synchronized(client.getKeyboardThread())
            {
                client.getKeyboardThread().notifyAll();
            }
        }
    }

    /*
     * builds an ack packet with the given block number
     */
    public byte[] buildACKPacket(byte[] blockNumber)
    {
        byte[] ack = new byte[4];
        ack[0] = 0;
        ack[1] = 4;
        ack[2] = blockNumber[0];
        ack[3] = blockNumber[1];
        return ack;
    }

    /*
     * when receiving a RRQ data packet we save it to a file
     */
    private void RRQData(byte[] msg)
    {
        // the first 6 bytes are not a part of the data
        byte[] data = Arrays.copyOfRange(msg, 6, msg.length);
        try {
            // append the data to the file
            Files.write(Paths.get(client.getRRQFileName()), data, StandardOpenOption.APPEND);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (msg.length < 518) // if the data is less than 518 bytes long, it is the last data packet
        {
            // after saving the file, we can wake up the keyboard thread
            synchronized(client.getKeyboardThread())
            {
                client.getKeyboardThread().notifyAll();
            }
        }
    }

}
