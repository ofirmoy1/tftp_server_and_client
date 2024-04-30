package bgu.spl.net.impl.tftp;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class listeningThread implements Runnable 
{
    private TftpClient client;

    public listeningThread(TftpClient client)
    {
        this.client = client;
    }
    @Override
    public void run()
    {
        OutputStream out = null;
        try {
            out = client.getSocket().getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        InputStream in = null;
        try {
            in = client.getSocket().getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] buffer = new byte[1024];
        int bytesReceived = -1;

        try {
          
            // read from the server, if in.read returns -1 then no bytes were read and the thread goes to blocking mode
            while (!shouldTerminate() && (bytesReceived = in.read(buffer))!= -1 ) 
            {
                for (int i = 0; i < bytesReceived; i++)
                {
                    byte[] result = client.getEncoderDecoder().decodeNextByte(buffer[i]);
                    if (result != null)
                    {
                      byte[] response = client.getEncoderDecoder().encode(client.getProtocol().process(result));
                      if (response != null)
                      {
                        try {
                            synchronized(client)
                                {
                                    out.write(response);
                                    out.flush();
                                }
                           
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                      }
                    }
                }
            }
           // System.out.println("Listening thread terminated");
        }
        catch (IOException e) {
            //System.out.println("socket was closed, Listening thread terminated");
        }
    
    }

    /**
     * @return true if the connection should be terminated
     */
    public boolean shouldTerminate()
    {
        return client.shouldTerminate(); 
    }
}
