package server.infra;

import common.IClientCallback;
import common.VectorClock;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Notificador / Broadcast.
 *
 * Responsabilidades (Pareja C):
 *  - Mantiene la lista de clientes conectados.
 *  - Hace broadcast del documento + vector clock a todos los clientes.
 *
 * El núcleo (EditorServiceImpl, Pareja B) delega aquí el broadcast.
 */
public class Notifier {

    // Lista sincronizada de callbacks de cliente
    private final List<IClientCallback> clients =
            Collections.synchronizedList(new ArrayList<>());

    private final int myServerId;
    private final ServerState serverState;

    public Notifier(int myServerId, ServerState serverState) {
        this.myServerId = myServerId;
        this.serverState = serverState;
    }

    public void registerClient(IClientCallback client, String initialDocument, VectorClock initialClock) {
        clients.add(client);
        System.out.println("[Server " + myServerId + "] Cliente registrado. Total: " + clients.size());

        // Enviamos el estado actual nada más conectarse
        try {
            client.updateDocument(initialDocument, initialClock);
        } catch (RemoteException e) {
            System.err.println("[Server " + myServerId + "] Error enviando estado inicial al cliente: " + e);
            clients.remove(client);
        }
    }

    public void unregisterClient(IClientCallback client) {
        clients.remove(client);
        System.out.println("[Server " + myServerId + "] Cliente desregistrado. Total: " + clients.size());
    }

    /**
     * Envía el documento y vector clock actuales a todos los clientes.
     * Sólo se ejecuta si este servidor es LÍDER.
     *
     * El núcleo (B) debe pasar un snapshot: (document, clock).
     */
    public void broadcast(String documentSnapshot, VectorClock clockSnapshot) {
        if (!serverState.isLeader()) {
            // Sólo el líder envia actualizaciones a los clientes
            return;
        }

        synchronized (clients) {
            Iterator<IClientCallback> it = clients.iterator();
            while (it.hasNext()) {
                IClientCallback client = it.next();
                try {
                    client.updateDocument(documentSnapshot, clockSnapshot);
                } catch (RemoteException e) {
                    System.out.println("[Server " + myServerId + "] Cliente no responde, lo elimino de la lista.");
                    it.remove();
                }
            }
        }
    }

    /**
     * Devuelve el número de clientes registrados (para logs, debugging…).
     */
    public int getClientCount() {
        return clients.size();
    }
}
