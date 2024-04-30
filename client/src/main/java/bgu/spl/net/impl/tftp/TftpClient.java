// mvn exec:java -D exec.mainClass="bgu.spl.net.impl.tftp.TftpServer" -D exec.args="7777" 
package bgu.spl.net.impl.tftp;
import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
public class TftpClient {
    /*
     * the keyboard thread
     */
    private Thread keyboardThread;
    
    /*
     * the listening thread
     */
    private Thread listeningThread;
    
    /*
     * the protocol
     */
    private TftpClientProtocol protocol;
    
    /*
     * the encoder decoder
     */
    private TftpEncoderDecoder encoderDecoder;
    
    /* 
     * the socket
     */
    private Socket socket;

    /*
     * error code in the last message received from the server, -1 if no error
     */
    private short error;

     /*
     * true if the last request was a DIRQ request, so the listening thread will know what to do when receiving a data packet
     */
    private boolean lastRequestIsDIRQ = false;

    /*
     * file names requested in the last DIRQ request
     */
    private List<String> filesNames;
    
    /*
     * the file name requested in the last RRQ request
     */
    private String RRQFileName;

    /*
     * true if the client is connected to the server, the client has to know that for the DISC command
     */
    private boolean isConnected;

    /*
     * constructor
     */
    public TftpClient(String serverIP, int serverPort) 
    {
        try {
            socket = new Socket(serverIP, serverPort);
            System.err.println("Connected to server!");
        } catch (IOException e) {
            System.out.println("Error connecting to server: " + e.getMessage());
            return;
        }
        protocol = new TftpClientProtocol(this);
        encoderDecoder = new TftpEncoderDecoder();
        keyboardThread = new Thread(new keyboardThread(this));
        listeningThread = new Thread(new listeningThread(this));
        error = -1; // no error
        filesNames = new LinkedList<String>();
        RRQFileName = "";
        isConnected = false;
        listeningThread.start();
        keyboardThread.start();
    }
    public static void main(String[] args) 
    {
        if (args.length != 2)
        {
            System.out.println("type: <serverIP> <serverPort>");
            return;
        }
        String serverIP = args[0];
        int serverPort = Integer.parseInt(args[1]);
        new TftpClient(serverIP, serverPort);
    }

    /*
     * returns the keyboard thread
     */
    public Thread getKeyboardThread()
    {
        return keyboardThread;
    }

    /*
     * returns the listening thread
     */
    public Thread getListeningThread()
    {
        return listeningThread;
    }

    /*
     * returns the protocol
     */
    public TftpClientProtocol getProtocol()
    {
        return protocol;
    }

    /*
     * returns the encoder decoder
     */
    public TftpEncoderDecoder getEncoderDecoder()
    {
        return encoderDecoder;
    }

    /*
     * returns the socket
     */
    public Socket getSocket()
    {
        return socket;
    }

    /*
     * returns true if the last message received from the server was an error
     */
    public boolean shouldTerminate()
    {
        return socket.isClosed(); // || listeningThread.isInterrupted() || keyboardThread.isInterrupted();
    }

    /*
     * setter to the error code
     */
    public void setError(short error)
    {
        this.error = error;
    }
    
    /*
     * getter to the error code
     */
    public short getError()
    {
        return error;
    }

     /*
     * a getter for the lastRequestIsDIRQ field
     */
    public boolean getLastRequestIsDIRQ()
    {
        return lastRequestIsDIRQ;
    }

    /*
     * a setter for the lastRequestIsDIRQ field
     */
    public void setLastRequestIsDIRQ(boolean lastRequestIsDIRQ)
    {
        this.lastRequestIsDIRQ = lastRequestIsDIRQ;
    }

    /*
     * a getter for the filesNames field
     */
    public List<String> getfilesNames()
    {
        return filesNames;
    }

    
    /*
     * a getter for the filesNames field
     */
    public String getRRQFileName()
    {
        return RRQFileName;
    }

    /*
     * a setter for the filesNames field
     */
    public void setRRQFileName(String RRQFileName)
    {
        this.RRQFileName = RRQFileName;
    }


    /*
     * a getter for the isConnected field
     */
    public boolean getIsConnected()
    {
        return isConnected;
    }

    /*
     * a setter for the isConnected field
     */
    public void setIsConnected(boolean isConnected)
    {
        this.isConnected = isConnected;
    }

    /*
     * closes the socket and interrupts the threads
     */
    public void terminate()
    {
            try {
                socket.close();
                System.out.println("connection closed");
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

}
