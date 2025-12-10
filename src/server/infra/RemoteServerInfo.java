package server.infra;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import common.IEditorService;

public class RemoteServerInfo {

    private final int serverId;
    private final String host;
    private final int port;
    private final String bindingName;

    private IEditorService cachedStub;

    public RemoteServerInfo(int serverId, String host, int port, String bindingName) {
        this.serverId = serverId;
        this.host = host;
        this.port = port;
        this.bindingName = bindingName;
    }

    public int getServerId() {
        return serverId;
    }

    public synchronized IEditorService getStub() throws Exception {
        if (cachedStub == null) {
            Registry registry = LocateRegistry.getRegistry(host, port);
            cachedStub = (IEditorService) registry.lookup(bindingName);
        }
        return cachedStub;
    }

    @Override
    public String toString() {
        return "Server[" + serverId + "@" + host + ":" + port + "/" + bindingName + "]";
    }
}
