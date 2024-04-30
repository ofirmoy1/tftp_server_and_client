package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
//holds the hash map of the login id to the username in static way
class ConnectionsHolder{
    static ConcurrentHashMap<Integer, String> loginIdToUsername=new ConcurrentHashMap<>();
}
public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    
    private int connectionId;
    private Connections<byte[]> connections;
    private FileOutputStream fileOutputStream;
    private File writingFile;
    private String writingFileName;
    private boolean terminate;
    private byte[] dirqData;
    private byte[] rrqData;
    private int dirqDataIndex;
    private int rrqDataIndex;
    private boolean dirqComplete;
    private boolean rrqComplete;
    private boolean wrqComplete;


    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        this.terminate=false;
        this.dirqComplete=true;
        this.rrqComplete=true;
        this.wrqComplete=true;
    }

    @Override
    public void process(byte[] message) {
        if (terminate)  return;
        short opCode = byte2short(new byte[]{message[0], message[1]});
        if (opCode != 7 && !ConnectionsHolder.loginIdToUsername.containsKey(connectionId)){
            //User not logged in
            sendError((byte)6,"");
            return;
        }
        switch(opCode){
            case 1:
                //read request(RRQ)
                rrq(message);
                break;
            case 2:
                //write request(WRQ)
                wrq(message);
                break;
            case 3:
                //DATA request
                data(message);
                break;
            case 4:
                //acnowledgment ACK
                ack(message);
                break;
            case 5:
                //ERROR request
                error(message);
                break;
            case 6:
                //directory listing request (DIRQ)
                dirq(message);
                break;
            case 7:
                //login request (LOGRQ)
                logrq(message);
                break;
            case 8:
                //delete request (DELRQ)
                delrq(message);
                break;
            case 9:
                //broadcast request (BCST)
                bcst(message);
                break;
            case 10:
                //diconect request (DISC)
                disc(message);
                break;
            default:
                //Illegal TFTP operation
                sendError((byte)4, "");
                break;
        }
    
    }

    @Override
    public boolean shouldTerminate() {
        if(terminate){
            ConnectionsHolder.loginIdToUsername.remove(connectionId);
            this.connections.disconnect(connectionId);
        }
        return terminate;
    }
    /*
     * This method is responsible for sending error packets to the client.
     */
    private void sendError(byte errorCode,String errorMsg){
        byte[] errorPacket=new byte[errorMsg.length()+5];
        errorPacket[0]=0;
        errorPacket[1]=5;
        errorPacket[2]=0;
        errorPacket[3]=errorCode;
        byte[] errorMsgBytes=errorMsg.getBytes();
        for(int i=0;i<errorMsgBytes.length;i++){
            errorPacket[i+4]=errorMsgBytes[i];
        }
        errorPacket[errorPacket.length-1]=0;
        try{
            connections.send(connectionId,errorPacket);
        }
        catch(Exception e){
            e.printStackTrace();
        }        
    }
    /*
     * This method is responsible for sending an ACK packet to the client.
     */
    private void sendAck(short blockNumber){
        byte[] ackPacket=new byte[4];
        ackPacket[0]=0;
        ackPacket[1]=4;
        byte[] blockNumberBytes=short2byte(blockNumber);
        ackPacket[2]=blockNumberBytes[0];
        ackPacket[3]=blockNumberBytes[1];
        try{
            connections.send(connectionId,ackPacket);
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }


    /*
     * This method is responsible for sending broadcast packets to the client.
     * addOrDel=0 for adding a file and addOrDel=1 for deleting a file.
     */
   private void sendBroadcast(byte[] message, byte addOrDel) {
        // add 1 byte for the 0 byte and 3 for the header
        byte[] broadcastPacket = new byte[message.length + 4];
        broadcastPacket[0] = 0;
        broadcastPacket[1] = 9;
        broadcastPacket[2] = addOrDel;
        for (int i = 0; i < message.length; i++) {
            broadcastPacket[i + 3] = message[i];
        }
        broadcastPacket[broadcastPacket.length - 1] = 0;
        try {
            connections.broadcast(broadcastPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    /*
     * This method sends data packets to the client.
     * dataType=0 for dirq data packets and dataType=1 for rrq data packets.
     * in case the data size is larger than 512 bytes, the data is divided into blocks of 512 bytes.
     * the methods returns true if this is the last block of data, otherwise it returns false.
     */
    private void sendDataPacket(int dataType,short blockNumber){
        byte[] data=(dataType==0 ? this.dirqData : this.rrqData);
        int dataStartIndex=(dataType==0 ? this.dirqDataIndex : this.rrqDataIndex); 
        short dataSize=(short)(data.length-dataStartIndex>=512 ? 512 : data.length-dataStartIndex);
        byte[] dataPacket=new byte[dataSize+6];
        dataPacket[0]=0;
        dataPacket[1]=3;
        byte[] dataSizeBytes=short2byte(dataSize);
        dataPacket[2]=dataSizeBytes[0];
        dataPacket[3]=dataSizeBytes[1];
        byte[] blockNumberBytes=short2byte(blockNumber);
        dataPacket[4]=blockNumberBytes[0];
        dataPacket[5]=blockNumberBytes[1];
        //copy the data to the data packet
        for(int i=0;i<dataSize;i++){
            dataPacket[i+6]=data[i+dataStartIndex];
        }
        try{
            connections.send(connectionId,dataPacket);
        }
        catch(Exception e){
            e.printStackTrace();
            //maybe we should send an error packet or send again the data packet
        }
        if(dataType==0)
        {
            this.dirqDataIndex+=dataSize;
            this.dirqComplete= dataSize<512;
        }
        else
        {
            this.rrqDataIndex+=dataSize;
            this.rrqComplete= dataSize<512;
        }
    } 
    /*
     * This method is responsible for reading requests from the client.
     */
    private void rrq(byte[] message) {
        String fileName = new String(message,2,message.length-3,StandardCharsets.UTF_8);
        File file=new File("Files/"+fileName);
        if(!file.exists()){
            sendError((byte)1,"");
            return;
        }
        else{
            try{
                FileInputStream fileInputStream=new FileInputStream(file);
                this.rrqData=new byte[(int)file.length()];
                synchronized(file){
                    fileInputStream.read(this.rrqData);
                }
                fileInputStream.close();
                this.rrqDataIndex=0;
                this.rrqComplete=false;
                sendDataPacket(1, (short)1);
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    private void wrq(byte[] message) {
        writingFileName = new String(message,2,message.length-3,StandardCharsets.UTF_8);
        writingFile=new File("Files/"+writingFileName);
        if(writingFile.exists()){
            //File already exists
            sendError((byte)5,"");
            return;
        }
        else{
            try{
                fileOutputStream=new FileOutputStream(writingFile,true);
                this.wrqComplete=false;
                sendAck((short)0);
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    /*
     * this method is responsible for sending to client the list of files in the server.
     */
    private void dirq(byte[] message) {
        File folder=new File("Files/");
        File[] files=folder.listFiles();
        int dataSize=0;
        ArrayList<byte[]> filesNames=new ArrayList<>();
        for(File file:files){
            if(file.isFile())
            {
                byte[] byteFileName=file.getName().getBytes();
                filesNames.add(byteFileName);
                //add 1 for the 0 byte
                dataSize+=byteFileName.length+1;
            }
        }
        byte[] data=new byte[dataSize];
        int dataIndex=0;
        for(int i=0;i<filesNames.size();i++){
            for(int j=0;j<filesNames.get(i).length;j++){
                data[dataIndex]=filesNames.get(i)[j];
                dataIndex++;
            }
            data[dataIndex]=0;
            dataIndex++;
        }
        this.dirqData=data;
        this.dirqComplete=false;
        this.dirqDataIndex=0;
        sendDataPacket(0,(short)1);
    }

    /*
     * this method is responsible for getting data from the client
     *  in order to wite it to the file it has access to.
     */
    private void data(byte[] message) {
        try{
            synchronized(writingFile){
                fileOutputStream.write(message,6,message.length-6);
            }
            this.wrqComplete = message.length < 518;
            sendAck(byte2short(new byte[]{message[4],message[5]}));
        }catch(Exception e){
            e.printStackTrace();
        }
        if (this.wrqComplete){
            try{
                fileOutputStream.close();
                 //move the file to the Files directory
                //  Path source = Paths.get(writingFileName);
                //  Path target = Paths.get("Files/" + writingFileName);
                //  Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            catch(Exception e){
                e.printStackTrace();
            }
            // send broadcast that the file was added
            sendBroadcast(writingFileName.getBytes(), (byte)1);
        }
    
    }

    private void ack(byte[] message) {
        short blockNum = byte2short(new byte[]{message[2], message[3]});
        if(blockNum >= 1 && !rrqComplete){
            blockNum++;
            sendDataPacket(1, blockNum);
        }
        else if(blockNum >= 1 && !dirqComplete){
            blockNum++;
            sendDataPacket(0, blockNum);
        }
        System.out.println("ACK " + blockNum);
    }




    /*
     * this method is responsible for handel logging the request.
     * This packet must be the first packet to be sent by the client
     * to the server, or an ERROR packet is returned. If successful an ACK packet will be sent in return.
     */
    private void logrq(byte[] message) {

        String userName= new String(message,2,message.length-3,StandardCharsets.UTF_8);
        if(ConnectionsHolder.loginIdToUsername.containsValue(userName)){
            sendError((byte)7,"");
            return;
        }
        else{
            ConnectionsHolder.loginIdToUsername.put(connectionId,userName);
            sendAck((short)0);
        }
    }

    /*
     * this method is responsible for handel deleting a file request.
     */
    private void delrq(byte[] message) {
        String fileName = new String(message,2,message.length-3,StandardCharsets.UTF_8);
        File file=new File("Files/"+fileName);
        if(!file.exists()){
            sendError((byte)1,"");
            return;
        }
        else{
            file.delete();
            sendAck((short)0);
            // send broadcast that the file was deleted
            sendBroadcast(fileName.getBytes(), (byte)0);
        }
    }


    private void bcst(byte[] message){
        //this type of massage is server to clinet only
        //thus sending Illegal TFTP operation error
        sendError((byte)4, "");
    }
    
    public void error(byte[] message) {
        short errorCode = byte2short(new byte[]{message[2], message[3]});
        String errorMsg = "";
        errorMsg = new String(message, 4, message.length-5, StandardCharsets.UTF_8);
        System.out.println("Error " + errorCode + " " + errorMsg);
    }


    /*
     * this method is responsible for handel disconnecting the client.
     */
    public void disc(byte[] message) {
        if(ConnectionsHolder.loginIdToUsername.containsKey(connectionId)){
            ConnectionsHolder.loginIdToUsername.remove(connectionId);
            sendAck((short)0);
            this.connections.disconnect(connectionId);
            this.terminate = true;
        }
        else{
            //send error 6
            sendError((byte)6, "");
        }
    }

    /*
     * converts a short to a 2 byte array
     */
    private byte[] short2byte(short num){
        byte [] bytes = new byte []{( byte ) ( num >> 8) , ( byte ) ( num & 0xff ) };
        return bytes;
    }
    /*
     * converts a 2 byte array to a short
     */
    private short byte2short(byte[] bytes){
        return ( short ) ((( short ) bytes [0]) << 8 | ( short ) ( bytes [1]) & 0xff );
    }
}
