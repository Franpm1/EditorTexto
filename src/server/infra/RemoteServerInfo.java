package server.infra;

import common.IEditorService;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RemoteServerInfo {
    private final int serverId;
    private final String host;
    private final int port;
    private final String bindingName;
    private IEditorService cachedStub;

    static {
        // Aumentamos a 2.5 segundos para dar tiempo a la reconexión y sincronización
        System.setProperty("sun.rmi.transport.tcp.responseTimeout", "2500");
        System.setProperty("sun.rmi.transport.tcp.readTimeout", "2500");
        System.setProperty("sun.rmi.transport.connectionTimeout", "2500");
        System.setProperty("sun.rmi.transport.proxy.connectTimeout", "2500");
        System.setProperty("sun.rmi.transport.tcp.handshakeTimeout", "2500");
        System.setProperty("sun.rmi.transport.tcp.connectionPoolSize", "4");
    }

    public RemoteServerInfo(int serverId, String host, int port, String bindingName) {
        this.serverId = serverId;
        this.host = host;
        this.port = port;
        this.bindingName = bindingName;
        this.cachedStub = null;
    }
    
    public int getServerId() { return serverId; }
    
    public IEditorService getStub() throws Exception {
        if (cachedStub != null) {
            try {
                cachedStub.heartbeat();
                return cachedStub;
            } catch (Exception e) {
                cachedStub = null;
            }
        }
        
        Registry registry = LocateRegistry.getRegistry(host, port);
        cachedStub = (IEditorService) registry.lookup(bindingName);
        return cachedStub;
    }
    
    public void invalidateStubCache() {
        cachedStub = null;
    }
    
    @Override
    public String toString() {
        return "Server " + serverId + " @ " + host + ":" + port;
    }
}