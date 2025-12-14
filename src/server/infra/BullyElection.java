package server.infra;

import common.IEditorService;
import common.VectorClockComparator;
import java.util.List;
import java.util.concurrent.*;

public class BullyElection {
    private final ServerState state;
    private final List<RemoteServerInfo> allServers;
    private final IEditorService myServiceStub;

    public BullyElection(ServerState state, List<RemoteServerInfo> allServers, IEditorService myServiceStub) {
        this.state = state;
        this.allServers = allServers;
        this.myServiceStub = myServiceStub;
    }

    public void startElectionOnStartup() {
        System.out.println("🔍 Iniciando elección al arrancar...");
        
        // IMPORTANTE: En el arranque, esperar un poco más antes de declararse líder
        try {
            Thread.sleep(1000); // Esperar 1 segundo para que todos arranquen
        } catch (InterruptedException e) {}
        
        boolean foundHigher = false;
        int myId = state.getMyServerId();
        
        // Intento 1: Buscar líder existente (cualquier ID, no solo mayores)
        System.out.println("Buscando líder existente...");
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() != myId) {
                Future<Boolean> future = Executors.newCachedThreadPool().submit(() -> {
                    try {
                        // Intentar obtener el estado para ver si es líder
                        info.getStub().getCurrentState();
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
                
                try {
                    if (future.get(1500, TimeUnit.MILLISECONDS)) {
                        System.out.println("✓ Servidor " + info.getServerId() + " responde");
                        // Preguntar quién es el líder
                        try {
                            // No hay método directo, pero podemos inferir
                            // Si responde, podría ser el líder
                            state.setCurrentLeaderId(info.getServerId());
                            System.out.println("Líder detectado: " + info.getServerId());
                            return; // Salir, ya tenemos líder
                        } catch (Exception e) {
                            // Continuar
                        }
                    }
                } catch (Exception e) {
                    future.cancel(true);
                }
            }
        }
        
        // Intento 2: Algoritmo Bully tradicional (solo si no hay líder)
        System.out.println("No hay líder detectado, iniciando elección Bully...");
        foundHigher = false;
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                Future<Boolean> future = Executors.newCachedThreadPool().submit(() -> {
                    try {
                        info.getStub().heartbeat();
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
                
                try {
                    if (future.get(1500, TimeUnit.MILLISECONDS)) {
                        foundHigher = true;
                        System.out.println("✓ Servidor " + info.getServerId() + " responde (ID mayor)");
                        state.setCurrentLeaderId(info.getServerId());
                        break;
                    }
                } catch (TimeoutException e) {
                    System.out.println("✗ Servidor " + info.getServerId() + " timeout");
                    future.cancel(true);
                } catch (Exception e) {
                    System.out.println("✗ Servidor " + info.getServerId() + " error");
                }
            }
        }

        if (!foundHigher) {
            System.out.println("✅ No hay servidores con ID mayor activos. Sincronizando...");
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        } else {
            System.out.println("⏳ Líder establecido: " + state.getCurrentLeaderId());
        }
    }

    // ... (el resto de los métodos se mantienen igual)
    public void onLeaderDown() {
        if (state.isLeader() || state.getCurrentLeaderId() != -1) {
            return;
        }
        
        System.out.println("⚡ Líder caído, iniciando elección...");
        boolean foundHigher = false;
        int myId = state.getMyServerId();
        
        ExecutorService quickPool = Executors.newCachedThreadPool();

        // Verificar TODOS los servidores, no solo los de ID mayor
        // (el líder podría tener ID menor)
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() != myId) {
                Future<Boolean> future = quickPool.submit(() -> {
                    try {
                        info.getStub().heartbeat();
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
                
                try {
                    if (future.get(1500, TimeUnit.MILLISECONDS)) {
                        System.out.println("✓ Nodo " + info.getServerId() + " responde");
                        // Verificar si este nodo es el líder
                        if (info.getServerId() == state.getCurrentLeaderId()) {
                            System.out.println("El líder " + info.getServerId() + " SÍ responde");
                            state.setCurrentLeaderId(info.getServerId());
                            return; // El líder sigue vivo
                        }
                        foundHigher = foundHigher || (info.getServerId() > myId);
                    }
                } catch (Exception e) {
                    future.cancel(true);
                }
            }
        }
        
        quickPool.shutdownNow();

        // Solo convertirse en líder si:
        // 1. No se encontró al líder actual
        // 2. No hay servidores con ID mayor vivos
        if (!foundHigher) {
            System.out.println("🔄 Sincronizando antes de convertirme en líder...");
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        } else {
            System.out.println("Hay servidores con ID mayor activos, esperando...");
        }
    }

    // ... (syncStateBeforeBecomingLeader y becomeLeaderNow se mantienen igual)
    
    private void syncStateBeforeBecomingLeader() {
        System.out.println("🔄 Sincronizando estado...");
        
        String latestContent = "";
        common.VectorClock latestClock = null;
        boolean gotState = false;
        
        ExecutorService syncPool = Executors.newCachedThreadPool();
        List<Future<common.DocumentSnapshot>> futures = new java.util.ArrayList<>();
        
        // Intentar con TODOS los servidores, no solo mayores
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            futures.add(syncPool.submit(() -> {
                try {
                    return info.getStub().getCurrentState();
                } catch (Exception e) {
                    return null;
                }
            }));
        }
        
        // Esperar respuestas con timeout más largo
        for (int i = 0; i < futures.size(); i++) {
            try {
                common.DocumentSnapshot snapshot = futures.get(i).get(2000, TimeUnit.MILLISECONDS);
                if (snapshot != null) {
                    System.out.println("Estado obtenido del servidor " + 
                        (i+1) + ": " + snapshot.getClock());
                    
                    if (!gotState) {
                        latestContent = snapshot.getContent();
                        latestClock = snapshot.getClock();
                        gotState = true;
                    } else if (VectorClockComparator.isClockNewer(snapshot.getClock(), latestClock)) {
                        latestContent = snapshot.getContent();
                        latestClock = snapshot.getClock();
                        System.out.println("Servidor tiene estado más reciente");
                    }
                }
            } catch (Exception e) {
                System.out.println("Timeout obteniendo estado del servidor " + (i+1));
            }
        }
        
        syncPool.shutdownNow();
        
        if (gotState) {
            try {
                myServiceStub.becomeLeader(latestContent, latestClock);
                System.out.println("Estado sincronizado correctamente");
            } catch (Exception e) {
                System.out.println("Error al convertirse en líder: " + e.getMessage());
            }
        } else {
            System.out.println("⚠️ No se pudo obtener estado de otros servidores");
        }
    }

    // ... (becomeLeaderNow y getCurrentLeaderInfo se mantienen igual)
}