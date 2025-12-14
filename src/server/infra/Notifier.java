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
        System.out.println("📋 Cliente registrado. Total: " + clients.size());
    }
    
    public int getClientCount() {
        return clients.size();
    }

    public void broadcast(String documentSnapshot, VectorClock clockSnapshot) {
        // Solo mostrar log si hay clientes
        if (clients.isEmpty()) {
            System.out.println("📢 Broadcast: 0 clientes (ninguno para notificar)");
            return;
        }
        
        System.out.println("📢 Broadcast a " + clients.size() + " cliente(s) locales");
        
        synchronized (clients) {
            var iterator = clients.iterator();
            while (iterator.hasNext()) {
                IClientCallback client = iterator.next();
                try {
                    client.syncState(documentSnapshot, clockSnapshot);
                } catch (RemoteException e) {
                    System.out.println("🔌 Cliente desconectado, removiendo...");
                    iterator.remove();
                }
            }
        }
    }
}