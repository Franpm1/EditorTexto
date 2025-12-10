# Editor Distribuido Cooperativo (RMI y Consistencia Causal)
Este proyecto implementa un editor de texto cooperativo con múltiples servidores, utilizando RMI (Invocación a Métodos Remotos) para la comunicación, el protocolo Bully para la elección de líder y Relojes Vectoriales para garantizar la Consistencia Causal entre clientes.

```
Pareja A (Cliente)
  "Interfaz de Usuario, Resiliencia del Cliente, Causalidad Local."
  "RMI Callback, Tolerancia a Fallos (Reconexión)."

Pareja B (Server Core)
  "Consistencia Causal, Exclusión Mutua Local, Arranque del Servicio."
  "Relojes Vectoriales, ReentrantReadWriteLock, Máquina de Estado Replicada."

Pareja C (Infraestructura)
  "Tolerancia a Fallos, Elección de Líder, Difusión Confiable."
  "Algoritmo Bully, Heartbeat, Gestión de Conexiones (Notifier)."
```

``` mermaid
graph TD
    %% Estilos de las parejas
    classDef StyleA fill:#e3f2fd,stroke:#1565c0,stroke-width:2px;
    classDef StyleB fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px;
    classDef StyleC fill:#ffebee,stroke:#c62828,stroke-width:2px;
    classDef StyleCommon fill:#fffde7,stroke:#fbc02d,stroke-width:2px,stroke-dasharray: 5 5;

    subgraph CLIENTE [PAREJA A: Acceso y Visualización]
        User((Usuario))
        UI[Consola UI]:::StyleA
        ClientLogic[Lógica Cliente RMI]:::StyleA
        User --> UI --> ClientLogic
    end

    subgraph SERVIDOR [SERVIDOR DISTRIBUIDO]
        direction TB
        
        subgraph INFRA [PAREJA C: Supervivencia]
            HB[Heartbeat & Bully]:::StyleC
            Net[Notifier / Broadcast]:::StyleC
            State[Estado Lider/Backup]:::StyleC
        end

        subgraph CORE [PAREJA B: Lógica y Datos]
            SrvImp[EditorServiceImpl]:::StyleB
            Doc[Document + Locks]:::StyleB
            VC[Vector Clock]:::StyleB
        end
        
        %% Conexiones Internas del Servidor
        HB -- 1. Vigila --> SrvImp
        SrvImp -- 2. Delega Red --> Net
        Net -- 3. Lee --> State
        SrvImp -- 4. Escribe --> Doc
    end

    %% Conexiones Externas
    ClientLogic == RMI: Write ==> SrvImp
    Net -.->|RMI: UpdateView| ClientLogic

    %% Notas explicativas
    noteA[A: Mantiene la sesión<br/>si el server cae]:::StyleA
    noteB[B: Garantiza que el<br/>dato sea coherente]:::StyleB
    noteC[C: Garantiza que siempre<br/>haya un Líder]:::StyleC

    ClientLogic -.- noteA
    Doc -.- noteB
    HB -.- noteC
```
