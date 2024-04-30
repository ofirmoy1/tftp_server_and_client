package bgu.spl.net.srv;


public interface Connections<T> {

    void connect(int connectionId, ConnectionHandler<T> handler);

    boolean send(int connectionId, T msg);

    void disconnect(int connectionId);

    //--------------------------added by us--------------------------
    boolean containsId(int connectionId);

    void broadcast(T msg);
}
