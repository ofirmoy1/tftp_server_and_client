package bgu.spl.net.impl.tftp;
import bgu.spl.net.srv.ConnectionHandler;
import java.util.concurrent.ConcurrentHashMap;
import bgu.spl.net.srv.Connections;

/*
 * This class map a unique ID for each active client connected to the server.
 */
public class ConnectionsImpl<T> implements Connections<T> {
    private ConcurrentHashMap<Integer, ConnectionHandler<T>> idToCH;

    public ConnectionsImpl() {
        idToCH = new ConcurrentHashMap<>();
    }
    
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        //add the connection to the map by its unique id
        idToCH.put(connectionId, handler);
    }

    public boolean send(int connectionId, T msg) {
        //get the connection by its unique id and send the message
        ConnectionHandler<T> ch = idToCH.get(connectionId);
        if (ch != null) {
            ch.send(msg);
            return true;
        }
        return false;
    }

    public void disconnect(int connectionId) {
        //remove the connection from the map by its unique id
        idToCH.remove(connectionId);
    }

    //--------------------------added by us--------------------------
    /*
     * This method checks if the map contains a connection with the given id.
     */
    public boolean containsId(int connectionId) {
        return idToCH.containsKey(connectionId);
    }

    public void broadcast(T msg) {
        //send the message to all the connected clients
        for (Integer key : idToCH.keySet()) {
            idToCH.get(key).send(msg);
            if (idToCH.get(key).isClosed())
                idToCH.remove(key);
        }

    }

}