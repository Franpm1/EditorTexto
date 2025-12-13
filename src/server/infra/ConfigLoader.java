package server.infra;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class ConfigLoader {
    public static List<RemoteServerInfo> loadConfig(String filename, int myId) {
        List<RemoteServerInfo> servers = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                String[] parts = line.split("=");
                if (parts.length != 2) continue;
                
                int serverId = Integer.parseInt(parts[0].trim());
                String[] addressParts = parts[1].split(":");
                if (addressParts.length != 2) continue;
                
                String ip = addressParts[0].trim();
                int port = Integer.parseInt(addressParts[1].trim());
                
                servers.add(new RemoteServerInfo(serverId, ip, port, "EditorService"));
                System.out.println("   - Servidor " + serverId + " en " + ip + ":" + port);
            }
        } catch (Exception e) {
            System.err.println("Error cargando config: " + e.getMessage());
            // Fallback: usar localhost
            System.out.println("Usando configuracion localhost por defecto");
            for (int i = 0; i < 6; i++) {
                servers.add(new RemoteServerInfo(i, "127.0.0.1", 1099 + i, "EditorService"));
            }
        }
        
        return servers;
    }
}