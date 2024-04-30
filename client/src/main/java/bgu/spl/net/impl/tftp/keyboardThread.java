package bgu.spl.net.impl.tftp;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class keyboardThread implements Runnable
{
    /*
     * the client
     */
    private TftpClient client;

   

    public keyboardThread(TftpClient client)
    {
        this.client = client;
    }
    @Override
    public void run()
    {
        Scanner scanner = new Scanner(System.in);
        OutputStream out = null;
        try {
            out = client.getSocket().getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (!shouldTerminate())
        {
            if (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] words = line.split(" ", 2);
                switch(words[0])
                {
                    case "DIRQ":
                    {
                        DIRQ_Command(words, out);
                        break;
                    }
                    case "DISC":
                    {
                        DISC_Command(words, out);
                        break;
                    }
                    case "RRQ":
                    {
                        RRQ_Command(words, out);
                        break;
                    }
                    case "WRQ":
                    {
                        WRQ_Command(words, out);
                        break;
                    }
                    case "LOGRQ":
                    {
                        LOGRQ_Command(words, out);
                        break;
                    }
                    case "DELRQ":
                    {
                        DELRQ_Command(words, out);
                        break;
                    }
                    default:
                    {
                        System.out.println("Invalid command");
                        break;
                    }
                }
                if (!shouldTerminate())
                { System.out.println("type your next command: ");}
             }
               
        }
        // System.out.println("keyboard thread terminated");
    }
    public byte[] build_RRQ_Packet(String fileName)
    {
        byte[] packet = build_general_packet_Type1(fileName);
        // add opcode
        packet[0] = 0;
        packet[1] = 1;
        return packet;
    }
    public byte[] build_WRQ_packet(String fileName)
    {
        byte[] packet = build_general_packet_Type1(fileName);
        // add opcode
        packet[0] = 0;
        packet[1] = 2;
        return packet;
    }
    public byte[] build_LOGRQ_packet(String fileName)
    {
        byte[] packet = build_general_packet_Type1(fileName);
        // add opcode
        packet[0] = 0;
        packet[1] = 7;
        return packet;
    }
    public byte[] build_DELRQ_packet(String fileName)
    {
        byte[] packet = build_general_packet_Type1(fileName);
        // add opcode
        packet[0] = 0;
        packet[1] = 8;
        return packet;
    }

    public byte[] build_general_packet_Type1(String fileName)
    {
        byte[] fileNameBytes = fileName.getBytes();
        byte[] packet = new byte[fileNameBytes.length + 3];
        for (int i = 0; i < fileNameBytes.length; i++)
        {
            if (fileNameBytes[i] == 0)
            {
                System.out.println("File contains 0 byte, invalid file name");
                return null;
            }
            packet[i+2] = fileNameBytes[i];
        }
        // to mark that the packet ends
        packet[packet.length - 1] = 0;
        return packet;
    }
    public byte[] build_DIRQ_packet()
    {
        byte[] packet = new byte[2];
        packet[0] = 0;
        packet[1] = 6;
        return packet;
    }
    public byte[] build_DISC_packet()
    {
        byte[] packet = new byte[2];
        packet[0] = 0;
        packet[1] = 10;
        return packet;
    }

    public byte[] build_DATA_packet(byte[] blockNumber, byte[] data)
    {
        byte[] dataSize = client.getEncoderDecoder().shortToTwoBytes((short) data.length);
        byte[] packet = new byte[data.length + 6]; // 6 bytes for the opcode, packetSize, block number
        // add opcode
        packet[0] = 0;
        packet[1] = 3;
        // add dataSize
        packet[2] = dataSize[0];
        packet[3] = dataSize[1];
        // add blockNumber
        packet[4] = blockNumber[0];
        packet[5] = blockNumber[1];
        for (int i = 0; i < data.length; i++)
        {
            packet[i+6] = data[i];
        }
        return packet;
    }
    
   /**
     * @return true if the connection should be terminated
     */
    public boolean shouldTerminate()
    {
        return client.shouldTerminate(); 
    }

    private void DIRQ_Command(String[] words, OutputStream out)
    {
        if (words.length == 1)
        {
            byte[] packet = build_DIRQ_packet();
            try {
                synchronized(client)
                {
                    out.write(packet);
                    out.flush();    
                }
                client.setLastRequestIsDIRQ(true);
                
                try {
                    synchronized(client.getKeyboardThread())
                    { client.getKeyboardThread().wait(); } // wait for the server to complete the transfer or send an ERROR
                    if (client.getError() == -1) // server completed the transfer of the files' names
                    {
                        // print the files' names
                        for (String fileName : client.getfilesNames())
                        {
                           System.out.println(fileName);
                        }
                    }
                    // clear the list of files' names for the next DIRQ request
                    client.getfilesNames().clear();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                   
                
                client.setLastRequestIsDIRQ(false);
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            
        }
        else
        {
            System.out.println("Invalid command");
        }
    }

    /*
     * if the client is connected, send a DISC packet to the server, anyway terminate the connection and exit the program
     */
    private void DISC_Command(String[] words, OutputStream out)
    {
        if (words.length == 1)
        {
            if (client.getIsConnected())
            {
                byte[] packet = build_DISC_packet();
                try {
                    synchronized(client)
                    {
                        out.write(packet);
                        out.flush();    
                    }
                    
                    try {
                        synchronized(client.getKeyboardThread()) {client.getKeyboardThread().wait();} // wait for the server to send an ACK or an ERROR
                        client.setIsConnected(false);  
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            client.terminate(); 
        }
        else
        {
            System.out.println("Invalid command");
        }
    }

    /*
     * send a RRQ packet to the server, if the file already exists print to terminal ”file already exists”
     */
   private void RRQ_Command(String[] words, OutputStream out)
    {
        if (words.length == 2)
        {
            // check if file already exists and then print to terminal ”file already exists” if it does          
            String filename = words[1];
            client.setRRQFileName(filename); // save the file name for the listening thread to know which file to read from the server
            File file = new File(filename);
            try {
                if (file.createNewFile()) { // File created successfully
                    byte[] packet = build_RRQ_Packet(words[1]);
                    if (packet == null) {return;} // meaning fileName contains 0 byte
                    try {
                        synchronized(client)
                        {
                            out.write(packet);
                            out.flush();    
                        }
                        try {
                            synchronized(client.getKeyboardThread()){client.getKeyboardThread().wait();} // wait for the server to complete the transfer or send an ERROR
                            if (client.getError() == -1) // server completed the transfer
                            {
                                System.out.println("RRQ "+ words[1] +" complete");
                            }
                            else // server sent an error
                            {
                                file.delete(); // delete the file
                            }
                            client.setRRQFileName(""); // reset the file name for the next RRQ request
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else { // File already exists
                    System.out.println("File already exists.");
                }
            } catch (IOException e) {
                System.out.println("An error in creating the file occurred.");
                e.printStackTrace();
            }
        }
        else
        {
            System.out.println("Invalid command");
        }
    }

    /*
     * upload a file to the server, if the file does not exist print to terminal ”file does not exists”
     */
    private void WRQ_Command(String[] words, OutputStream out)
    {
        if (words.length == 2)
        {
            // check if the file already exists, if File does not exist in the client side: print to terminal ”file does not exists” and don’t send WRQ packet to the server.
            String filename = words[1]; 
            Path filePath = Paths.get(filename);
            if (Files.exists(filePath)) { // File exists
                byte[] packet = build_WRQ_packet(words[1]);
                if (packet == null) {return;} // meaning fileName contains 0 byte
                try {
                    synchronized(client)
                    {
                    out.write(packet);
                    out.flush();    
                    }
                    try {
                        synchronized(client.getKeyboardThread()) {client.getKeyboardThread().wait();} // wait for the server to send an ACK or an ERROR
                        if (client.getError() == -1) // server sent an ACK
                        {
                            try {
                            transferFile(filename, out); // transfer the file and stop if the server sent an ERROR
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        else // server sent an ERROR
                        {
                            return; // no need to start the transfer
                        }
                        
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    
                    
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
            } else { // File doesn't exist
                System.out.println("File "+ words[1] +" does not exists");
            }
        }
        else
        {
            System.out.println("Invalid command");
        }
    }

    private void transferFile(String fileName, OutputStream out) throws IOException {
        // creating an input stream to read from the file
        try (InputStream in = new BufferedInputStream(new FileInputStream(fileName))) {
            byte[] buffer = new byte[512]; // the maximum size of the data part in a data packet
            int bytesRead;
            short blockNumber = 1;
            while ((bytesRead = in.read(buffer)) != -1) // read from the file until the end of it
            {
                // to send only the exact bytes read from the file
                byte[] exactBytes = new byte[bytesRead];
                System.arraycopy(buffer, 0, exactBytes, 0, bytesRead);
                // build a DATA packet to send to the server
                byte[] packet = build_DATA_packet(client.getEncoderDecoder().shortToTwoBytes(blockNumber), exactBytes);
                synchronized(client)
                {
                    out.write(packet);
                    out.flush();
                }
                    try {
                        synchronized(client.getKeyboardThread()) {client.getKeyboardThread().wait();} // wait for the server to send an ACK or an ERROR
                        if (client.getError() != -1) // server sent an ERROR
                        {
                            return; // server sent an ERROR so we should stop the transfer
                        }
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                blockNumber++; 
            }
            System.out.println("WRQ "+ fileName +" complete");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * login to the server
     */
    private void LOGRQ_Command(String[] words, OutputStream out)
    {
        if (words.length == 2)
        {
            byte[] packet = build_LOGRQ_packet(words[1]);
            if (packet == null) {return;} // meaning fileName contains 0 byte
            try {
                synchronized(client)
                {
                    out.write(packet);
                    out.flush();    
                }
                try {
                    synchronized(client.getKeyboardThread()) {client.getKeyboardThread().wait();} // wait for the server to send an ACK or an ERROR
                    // server sent an ack or user already logged in error
                    if (client.getError() == -1 || client.getError() == 7 ) 
                    {
                        client.setIsConnected(true); 
                    }
                } catch (InterruptedException e) {
                        System.out.println("An error occurred while trying to wait.");
                        e.printStackTrace();
                    }
            } catch (IOException e) {
                System.out.println("An error occurred while trying to write.");
                e.printStackTrace();
            }
        }
        else
        {
            System.out.println("Invalid command");
        }
    }

    /*
     * delete a file from the server
     */
    private void DELRQ_Command(String[] words, OutputStream out)
    {
        if (words.length == 2)
        {
            byte[] packet = build_DELRQ_packet(words[1]);
            if (packet == null) {return;} // meaning fileName contains 0 byte
            try {
                synchronized(client)
                {
                    out.write(packet);
                    out.flush();    
                }
                try {
                    synchronized(client.getKeyboardThread()){client.getKeyboardThread().wait();} // wait for the server to send an ACK or an ERROR
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else
        {
            System.out.println("Invalid command");
        }
    }
}
