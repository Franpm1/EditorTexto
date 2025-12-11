package server.infra;

import common.IEditorService;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RemoteServerInfo {
    private final int serverId;
    private final String host;
    private final int port;
    private final String bindingName;

    public RemoteServerInfo(int serverId, String host, int port, String bindingName) {
        this.serverId = serverId;
        this.host = host;
        this.port = port;
        this.bindingName = bindingName;
    }
    
    public int getServerId() { return serverId; }
    
    public IEditorService getStub() throws Exception {
        Registry registry = LocateRegistry.getRegistry(host, port);
        return (IEditorService) registry.lookup(bindingName);
    }
}