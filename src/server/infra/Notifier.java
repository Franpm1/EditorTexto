package server.infra;

import common.IClientCallback;
import common.VectorClock;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Notifier {
    private final List<IClientCallback> clients = Collections.synchronizedList(new ArrayList<>());
    private final ServerState serverState;

    public Notifier(ServerState serverState) {
        this.serverState = serverState;
    }

    public void registerClient(IClientCallback client) {
        clients.add(client);
        System.out.println(" Cliente registrado. Total: " + clients.size());
    }

    public void broadcast(String documentSnapshot, VectorClock clockSnapshot) {
        // Solo el líder debe hacer broadcast
        if (!serverState.isLeader()) {
            System.out.println(" Intento de broadcast desde no-líder. Ignorando.");
            return;
        }
        
        System.out.println(" Broadcast a " + clients.size() + " clientes");
        
        synchronized (clients) {
            var iterator = clients.iterator();
            while (iterator.hasNext()) {
                IClientCallback client = iterator.next();
                try {
                    client.syncState(documentSnapshot, clockSnapshot);
                    System.out.println(" Cliente notificado");
                } catch (RemoteException e) {
                    System.out.println(" Cliente desconectado, removiendo...");
                    iterator.remove();
                }
            }
        }
    }
}