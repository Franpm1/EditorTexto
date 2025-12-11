package server.core;

import common.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import server.infra.*;

public class EditorServiceImpl extends UnicastRemoteObject implements IEditorService {

    private final Document document;
    private final Notifier notifier;
    private IServerConnector backupConnector;
    private final ServerState serverState;

    public EditorServiceImpl(Document doc, Notifier notifier, ServerState state) throws RemoteException {
        super();
        this.document = doc;
        this.notifier = notifier;
        this.serverState = state;
    }
    
    public void setBackupConnector(IServerConnector sc) { 
        this.backupConnector = sc; 
    }

    @Override
    public void executeOperation(Operation op) throws RemoteException {
        System.out.println(" Operación recibida: " + op.getType() + " de " + op.getOwner());
        
        // 1. Aplicar la operación al documento local
        document.applyOperation(op);
        
        // 2. Solo si soy líder, notificar a todos los clientes
        if (serverState.isLeader()) {
            System.out.println("👑 Soy líder, haciendo broadcast...");
            notifier.broadcast(document.getContent(), document.getClockCopy());
            
            // 3. Replicar a backups (otros servidores)
            if (backupConnector != null) {
                backupConnector.propagateToBackups(document.getContent(), document.getClockCopy());
            }
        } else {
            System.out.println(" No soy líder, solo aplicando localmente");
            // Como no-líder, también debemos notificar a nuestros clientes locales
            notifier.broadcast(document.getContent(), document.getClockCopy());
        }
    }

    @Override
    public void registerClient(IClientCallback client, String username) throws RemoteException {
        System.out.println(" Registrando cliente: " + username);
        notifier.registerClient(client);
        
        // Enviar estado actual inmediatamente
        client.syncState(document.getContent(), document.getClockCopy());
    }

    @Override
    public void heartbeat() throws RemoteException {
        // Simple respuesta de que estoy vivo
    }

    @Override
    public void becomeLeader(String doc, VectorClock clock) throws RemoteException {
        System.out.println(" Recibiendo traspaso de liderazgo...");
        document.overwriteState(doc, clock);
        serverState.setLeader(true);
        serverState.setCurrentLeaderId(serverState.getMyServerId());
        System.out.println(" Ahora soy el líder.");
    }

    @Override
    public void declareLeader(int leaderId) throws RemoteException {
        System.out.println(" Servidor " + leaderId + " se ha declarado LÍDER.");
        serverState.setCurrentLeaderId(leaderId);
        serverState.setLeader(leaderId == serverState.getMyServerId());
    }

    @Override
    public void applyReplication(String doc, VectorClock clock) throws RemoteException {
        System.out.println(" Replicación recibida del líder");
        document.overwriteState(doc, clock);
        
        // Notificar a nuestros clientes locales del cambio
        notifier.broadcast(document.getContent(), document.getClockCopy());
    }
}