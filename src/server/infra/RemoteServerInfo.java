package server;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import common.IEditorService;

/**
 * Representa la información necesaria para contactar a otro servidor dentro del
 * sistema distribuido. Incluye host, puerto, nombre de binding RMI y un stub
 * cacheado para evitar búsquedas repetidas en el registro.
 */
public class RemoteServerInfo {

    private final int serverId;
    private final String host;
    private final int port;
    private final String bindingName;

    // Cache del stub RMI para evitar buscarlo en cada llamada
    private IEditorService stub;

    public RemoteServerInfo(int serverId, String host, int port, String bindingName) {
        this.serverId = serverId;
        this.host = host;
        this.port = port;
        this.bindingName = bindingName;
    }

    /** Devuelve el ID lógico del servidor. */
    public int getServerId() {
        return serverId;
    }

    /**
     * Obtiene el stub remoto asociado a este servidor. Si no se ha obtenido aún,
     * se recupera del registro RMI y se almacena en cache.
     */
    public synchronized IEditorService getStub() throws Exception {
        if (stub == null) {
            Registry registry = LocateRegistry.getRegistry(host, port);
            stub = (IEditorService) registry.lookup(bindingName);
        }
        return stub;
    }

    @Override
    public String toString() {
        return "Server[" + serverId + "@" + host + ":" + port + "/" + bindingName + "]";
    }
}
