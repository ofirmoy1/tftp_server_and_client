package bgu.spl.net.impl.tftp;
import java.util.function.Supplier;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.BaseServer;
import bgu.spl.net.srv.BlockingConnectionHandler;

public class TftpServer extends BaseServer<byte[]>{
    private Connections<byte[]> connections;
    private int connectionId;

    public TftpServer(int port, Supplier<BidiMessagingProtocol<byte[]>> protocolFactory, Supplier<MessageEncoderDecoder<byte[]>> encdecFactory) {
        super(port, protocolFactory, encdecFactory);
        connections = new ConnectionsImpl<>();
        connectionId = 0;
    }

    @Override
    protected void execute(BlockingConnectionHandler<byte[]> handler) {
        handler.start(connectionId++, connections);
        new Thread(handler).start();
    }

    public static void main(String[] args) {
        String arg = (args.length > 0) ? args[0] : "7777";
        int port = Integer.decode(arg).intValue();
        TftpServer server = new TftpServer(
                port,
                TftpProtocol::new,
                TftpEncoderDecoder::new
        );
        server.serve();
    }
}
