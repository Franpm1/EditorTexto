package server.infra;

import common.IEditorService;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RemoteServerInfo {
    private final int serverId;
    private final String host;
    private final int port;
    private final String bindingName;
    private IEditorService cachedStub; // Cache del stub RMI

    public RemoteServerInfo(int serverId, String host, int port, String bindingName) {
        this.serverId = serverId;
        this.host = host;
        this.port = port;
        this.bindingName = bindingName;
        this.cachedStub = null; // Inicialmente sin cache
    }
    
    public int getServerId() { return serverId; }
    
    public IEditorService getStub() throws Exception {
        // Si ya tenemos el stub cacheado y parece válido, usarlo
        if (cachedStub != null) {
            try {
                // Verificación rápida con heartbeat (opcional pero seguro)
                cachedStub.heartbeat();
                return cachedStub;
            } catch (Exception e) {
                // Stub cacheado no funciona, limpiar cache
                cachedStub = null;
                System.out.println("Cache invalida para servidor " + serverId + ", reconectando...");
            }
        }
        
        // Obtener stub nuevo y cachearlo
        Registry registry = LocateRegistry.getRegistry(host, port);
        cachedStub = (IEditorService) registry.lookup(bindingName);
        return cachedStub;
    }
    
    // Método para forzar recacheo si hay problemas
    public void invalidateStubCache() {
        cachedStub = null;
    }
    
    // Para depuración
    @Override
    public String toString() {
        return "Server " + serverId + " @ " + host + ":" + port + " [cache: " + (cachedStub != null ? "SI" : "NO") + "]";
    }
}