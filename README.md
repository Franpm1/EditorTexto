# Editor Colaborativo Distribuido
Sistema de edición colaborativa en tiempo real implementado en **Java RMI** bajo una arquitectura **Primary-Backup** para alta disponibilidad. 
Incorpora el **Algoritmo de Bully** para la elección dinámica de líder y utiliza **Relojes Vectoriales** (Vector Clocks) para gestionar la causalidad y consistencia eventual entre réplicas.

``` mermaid
flowchart TD
    Start((Inicio)) --> CheckLeader{¿Soy el Líder?}
    
    %% Bucle principal si soy líder
    CheckLeader -- SÍ --> Sleep[Dormir Intervalo]
    Sleep --> CheckLeader
    
    %% Si NO soy líder
    CheckLeader -- NO --> FindLeader[Buscar Info del Líder actual]
    FindLeader --> PingLeader{¿Ping al Líder?}
    
    %% Si el líder responde, vuelvo a dormir
    PingLeader -- Responde --> Sleep
    
    %% Si el líder falla, inicio la lógica de elección (SIN SUBGRAPH)
    PingLeader -- Falla/Null --> Election[Inicia BullyElection]
    
    Election --> SearchHigher[Buscar Nodos con ID > mi ID]
    SearchHigher --> HigherExists{¿Responde alguno?}
    
    %% Si existe uno mayor, simplemente duermo y espero
    HigherExists -- SÍ --> Sleep
    
    %% Si no hay nadie mayor, tomo el liderazgo
    HigherExists -- NO --> BecomeLeader[¡Me convierto en Líder!]
    BecomeLeader --> Declare[Enviar 'declareLeader' a todos los menores]
    
    %% Vuelta al inicio
    Declare --> CheckLeader

    %% ESTILOS
    %% linkStyle 2 es Sleep --> CheckLeader (negra discontinua)
    %% linkStyle 12 es Declare --> CheckLeader (negra discontinua)
    linkStyle 2,12 stroke:black,stroke-width:1px,stroke-dasharray: 5 5;
```

```mermaid
graph TD
    %% Estilos para diferenciar roles
    classDef client fill:#e1f5fe,stroke:#01579b,stroke-width:2px;
    classDef leader fill:#fff9c4,stroke:#fbc02d,stroke-width:4px;
    classDef backup fill:#f5f5f5,stroke:#9e9e9e,stroke-width:2px,stroke-dasharray: 5 5;

    subgraph Nodos_Clientes [Capa de Clientes]
        C1(Cliente A):::client
        C2(Cliente B):::client
        C3(Cliente C):::client
    end

    subgraph Nodos_Servidores [Cluster de Servidores - RMI]
        S1(Servidor X: BACKUP):::backup
        S2(Servidor Y: LÍDER):::leader
        S3(Servidor Z: BACKUP):::backup
    end

    %% 1. ENLACES Y FLUJO DE OPERACIÓN (Escritura desde Backup)
    C1 -- "1. executeOperation(op) <br/> [RMI]" --> S1
    
    %% Redirección al líder
    S1 -.-> |"2. Redirección al Líder <br/> (FindLeader + RMI)"| S2

    %% 2. PROPAGACIÓN DE CAMBIOS (Desde el Líder)
    S2 == "3. propagateToBackups <br/> (Replicación)" ==> S1
    S2 == "3. propagateToBackups <br/> (Replicación)" ==> S3

    %% 3. ACTUALIZACIÓN A CLIENTES (Callbacks)
    S2 -- "4. syncState <br/> (Callback)" --> C2
    S1 -- "5. syncState <br/> (Callback)" --> C1
    S3 -- "5. syncState <br/> (Callback)" --> C3

    %% Nota explicativa
    linkStyle 0 stroke:blue,stroke-width:2px;
    linkStyle 1 stroke:orange,stroke-width:2px;
    linkStyle 2,3 stroke:green,stroke-width:3px;
```

```mermaid
classDiagram
    %% Interfaces Comunes
    class IEditorService {
        <<interface>>
        +executeOperation(op)
        +registerClient(client, username)
        +heartbeat()
        +declareLeader(id)
        +applyReplication(doc, clock)
    }
    class IClientCallback {
        <<interface>>
        +syncState(doc, clock)
    }

    %% Cliente
    class ClientImpl {
        -ConsoleUI ui
        +syncState()
    }
    class ConsoleUI {
        -IEditorService server
        +start()
        +processCommand()
    }

    %% Servidor Core
    class EditorServiceImpl {
        -Document document
        -ServerState serverState
        -Notifier notifier
        +executeOperation()
    }
    class Document {
        -StringBuilder content
        -VectorClock vectorClock
        +applyOperation()
        +overwriteState()
    }
    
    %% Servidor Infraestructura
    class ServerState {
        -boolean isLeader
        -int currentLeaderId
    }
    class BullyElection {
        +startElectionOnStartup()
        +onLeaderDown()
    }
    class HeartbeatMonitor {
        +run()
    }
    class Notifier {
        -List~IClientCallback~ clients
        +broadcast()
    }

    %% Relaciones
    IEditorService <|.. EditorServiceImpl
    IClientCallback <|.. ClientImpl
    
    ClientImpl --> ConsoleUI : actualiza
    ConsoleUI --> IEditorService : llama (RMI)
    
    EditorServiceImpl --> Document : modifica
    EditorServiceImpl --> ServerState : consulta estado
    EditorServiceImpl --> Notifier : usa
    
    HeartbeatMonitor --> BullyElection : activa elección
    BullyElection --> ServerState : modifica líder
    BullyElection --> IEditorService : llama a otros nodos
```

```mermaid
sequenceDiagram
    autonumber
    participant ClientA as Cliente A
    participant ServerBackup as Servidor Backup
    participant ServerLeader as Servidor Líder
    participant ClientB as Cliente B

    Note over ClientA, ServerBackup: 1. Cliente envía operación al Backup
    ClientA->>ServerBackup: executeOperation(op)
    
    activate ServerBackup
    %% Lógica en EditorServiceImpl.java
    ServerBackup->>ServerBackup: isLeader() -> FALSE
    ServerBackup->>ServerBackup: findLeaderInfo()
    
    Note right of ServerBackup: 2. Redirección RMI (Sin aplicar localmente)
    ServerBackup->>ServerLeader: executeOperation(op)
    deactivate ServerBackup

    activate ServerLeader
    %% Lógica en EditorServiceImpl.java (Líder)
    ServerLeader->>ServerLeader: isLeader() -> TRUE
    
    Note right of ServerLeader: 3. Escritura Local (Document.java)
    ServerLeader->>ServerLeader: document.applyOperation(op)
    
    Note right of ServerLeader: 4. Notificar Clientes Locales
    %% Lógica en Notifier.java
    ServerLeader->>ClientB: syncState(fullDoc, clock)
    
    Note right of ServerLeader: 5. Replicación (ServerConnectorImpl)
    %% Hilo separado en ServerConnectorImpl.java
    ServerLeader-)ServerBackup: applyReplication(fullDoc, clock)
    deactivate ServerLeader

    activate ServerBackup
    Note left of ServerBackup: 6. Backup recibe nuevo estado
    %% Lógica en EditorServiceImpl.java (applyReplication)
    ServerBackup->>ServerBackup: document.overwriteState(doc, clock)
    
    Note left of ServerBackup: 7. Notificar Clientes del Backup
    ServerBackup->>ClientA: syncState(fullDoc, clock)
    deactivate ServerBackup
```
```mermaid
graph TD
    subgraph LAN [Red Local]
        style LAN fill:#f9f9f9,stroke:#333,stroke-width:2px
        
        subgraph Machine1 [LÍDER]
            style Machine1 fill:#e1f5fe,stroke:#01579b
            S0((Servidor 0<br/>LÍDER))
            C1[Cliente Local]
        end
        
        subgraph Machine2 [BACKUP]
            style Machine2 fill:#fff3e0,stroke:#e65100
            S1((Servidor 1<br/>BACKUP))
            C2[Cliente Remoto]
        end
    end

    %% FLUJO DE ESCRITURA
    C1 -- 1. RMI (Insert) --> S0
    
    %% FLUJO DE ACTUALIZACIÓN (Sync)
    %% Ajuste: El líder también actualiza a su propio cliente
    S0 -- 2. RMI (Sync) --> C1
    
    %% REPLICACIÓN
    S0 -- 3. RMI (Replicación) --> S1
    S1 -- 4. RMI (Sync) --> C2
    
    %% HEARTBEATS (Vigilancia)
    S1 -. RMI (Heartbeat) .-> S0

    %% ESTILOS
    %% Enlaces de datos (Verdes)
    linkStyle 0,1,2,3 stroke-width:2px,fill:none,stroke:green;
    
    %% Enlace de Heartbeat (Rojo Punteado)
    linkStyle 4 stroke-width:1px,fill:none,stroke:red,stroke-dasharray: 5 5;
```