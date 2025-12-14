package server.infra;

import common.DocumentSnapshot;
import common.IEditorService;
import common.VectorClock;
import java.util.List;
import java.util.concurrent.*;

public class BullyElection {
    private final ServerState state;
    private final List<RemoteServerInfo> allServers;
    private final IEditorService myServiceStub;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public BullyElection(ServerState state, List<RemoteServerInfo> allServers, IEditorService myServiceStub) {
        this.state = state;
        this.allServers = allServers;
        this.myServiceStub = myServiceStub;
    }

    public void startElectionOnStartup() {
        System.out.println("Buscando servidores con ID mayor al mío (" + state.getMyServerId() + ")...");
        
        // Usar threads paralelos para no bloquear
        List<Future<Boolean>> futures = new CopyOnWriteArrayList<>();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > state.getMyServerId()) {
                futures.add(executor.submit(() -> {
                    try {
                        System.out.println("  Probando servidor " + info.getServerId() + "...");
                        info.getStub().heartbeat();
                        System.out.println("  ✓ Servidor " + info.getServerId() + " responde");
                        return true;
                    } catch (Exception e) {
                        System.out.println("  ✗ Servidor " + info.getServerId() + " no disponible");
                        return false;
                    }
                }));
            }
        }
        
        // Esperar máximo 3 segundos por respuestas
        boolean foundHigher = false;
        try {
            for (Future<Boolean> future : futures) {
                try {
                    if (future.get(3, TimeUnit.SECONDS)) {
                        foundHigher = true;
                    }
                } catch (TimeoutException e) {
                    future.cancel(true);
                }
            }
        } catch (Exception e) {
            // Ignorar
        }
        
        if (!foundHigher) {
            System.out.println("✅ No hay servidores con ID mayor. Soy el líder.");
            
            // Sincronización ASINCRONA (no bloquea)
            new Thread(() -> {
                syncStateBeforeBecomingLeader();
                becomeLeaderNow();
            }).start();
        } else {
            System.out.println("⏳ Esperando notificación del líder...");
        }
    }

    public void onLeaderDown() {
        if (state.isLeader() || state.getCurrentLeaderId() != -1) {
            return;
        }
        
        System.out.println("Detectado fallo de líder. Iniciando elección...");
        
        // Similar a startElectionOnStartup pero más rápido
        boolean foundHigher = false;
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > state.getMyServerId()) {
                try {
                    // Timeout más corto para elección rápida
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            info.getStub().heartbeat();
                            return true;
                        } catch (Exception e) {
                            return false;
                        }
                    }).get(2, TimeUnit.SECONDS); // Solo 2 segundos de espera
                    
                    foundHigher = true;
                    System.out.println("✓ Nodo " + info.getServerId() + " responde");
                    break; // Encontré uno, no necesito seguir
                } catch (Exception e) {
                    // Timeout o error
                }
            }
        }

        if (!foundHigher) {
            // Sincronización en segundo plano
            new Thread(() -> {
                syncStateBeforeBecomingLeader();
                becomeLeaderNow();
            }).start();
        }
    }

    private void syncStateBeforeBecomingLeader() {
        System.out.println("Sincronizando estado (en segundo plano)...");
        
        // Solo intentar con el primer servidor que responda, no con todos
        String latestContent = "";
        VectorClock latestClock = null;
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            try {
                // Timeout corto: 2 segundos máximo por servidor
                DocumentSnapshot snapshot = CompletableFuture.supplyAsync(() -> {
                    try {
                        return info.getStub().getCurrentState();
                    } catch (Exception e) {
                        return null;
                    }
                }).get(2, TimeUnit.SECONDS);
                
                if (snapshot != null) {
                    System.out.println("Estado obtenido del servidor " + info.getServerId());
                    latestContent = snapshot.getContent();
                    latestClock = snapshot.getClock();
                    
                    // Usar el primer estado que obtengamos y salir
                    break;
                }
            } catch (Exception e) {
                System.out.println("Timeout con servidor " + info.getServerId() + ", probando siguiente...");
            }
        }
        
        try {
            if (latestClock != null) {
                myServiceStub.becomeLeader(latestContent, latestClock);
                System.out.println("Estado sincronizado desde otro servidor");
            } else {
                System.out.println("Inicio con documento vacío (no se pudo sincronizar)");
                myServiceStub.becomeLeader("", new VectorClock(allServers.size()));
            }
        } catch (Exception e) {
            System.err.println("Error en sincronización: " + e.getMessage());
        }
    }

    private void becomeLeaderNow() {
        System.out.println("Me proclamo LÍDER (ID " + state.getMyServerId() + ")");
        
        state.setLeader(true);
        state.setCurrentLeaderId(state.getMyServerId()); 
        
        // Notificar a otros servidores EN PARALELO
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            executor.submit(() -> {
                try {
                    info.getStub().declareLeader(state.getMyServerId());
                    System.out.println("✓ Notificado a servidor " + info.getServerId());
                } catch (Exception e) {
                    System.out.println("✗ No se pudo notificar a servidor " + info.getServerId());
                }
            });
        }
        
        System.out.println("✅ Ahora acepto escrituras como líder");
    }

    public RemoteServerInfo getCurrentLeaderInfo() {
        int leaderId = state.getCurrentLeaderId();
        if (leaderId == -1) return null;
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == leaderId) {
                return info;
            }
        }
        return null;
    }
}