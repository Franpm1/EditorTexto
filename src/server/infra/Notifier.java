package server.infra;

import common.IClientCallback;
import common.VectorClock;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Notifier {

    private final List<IClientCallback> clients =
            Collections.synchronizedList(new ArrayList<>());

    private final int myServerId;
    private final ServerState serverState;

    public Notifier(int myServerId, ServerState serverState) {
        this.myServerId = myServerId;
        this.serverState = serverState;
    }

    // CORRECCIÓN: Ajustado para coincidir con la llamada en EditorServiceImpl
    public void registerClient(IClientCallback client, String username) {
        clients.add(client);
        System.out.println("[Server " + myServerId + "] Cliente registrado: " + username + " (Total: " + clients.size() + ")");
        
        // NOTA: La sincronización inicial (syncState) la hace EditorServiceImpl,
        // así que no hace falta hacerla aquí.
    }

    public void unregisterClient(IClientCallback client) {
        clients.remove(client);
    }

    public void broadcast(String documentSnapshot, VectorClock clockSnapshot) {
        // Solo el líder notifica a los clientes para evitar mensajes duplicados
        // o estados inconsistentes si un backup va con retraso.
        if (!serverState.isLeader()) return; 

        synchronized (clients) {
            Iterator<IClientCallback> it = clients.iterator();
            while (it.hasNext()) {
                IClientCallback client = it.next();
                try {
                    client.updateDocument(documentSnapshot, clockSnapshot);
                } catch (RemoteException e) {
                    System.out.println("[Server " + myServerId + "] Cliente no responde, eliminado.");
                    it.remove();
                }
            }
        }
    }
}