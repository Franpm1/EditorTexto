package common;

public class VectorClockComparator {
    
    // Método estático para comparar eficientemente dos VectorClocks
    public static boolean isClockNewer(VectorClock clock1, VectorClock clock2) {
        if (clock1 == null || clock2 == null) return false;
        
        // Acceso directo a los arrays internos (necesitarías getter en VectorClock)
        // Como no hay getter, usamos toString pero optimizado
        
        String s1 = clock1.toString();
        String s2 = clock2.toString();
        
        // Parsear una sola vez
        int[] v1 = parseVectorString(s1);
        int[] v2 = parseVectorString(s2);
        
        return isClockNewer(v1, v2);
    }
    
    // Método privado que hace la comparación real con arrays
    private static boolean isClockNewer(int[] v1, int[] v2) {
        boolean atLeastOneGreater = false;
        boolean atLeastOneLess = false;
        
        int minLength = Math.min(v1.length, v2.length);
        
        for (int i = 0; i < minLength; i++) {
            if (v1[i] > v2[i]) atLeastOneGreater = true;
            if (v1[i] < v2[i]) atLeastOneLess = true;
        }
        
        return atLeastOneGreater && !atLeastOneLess;
    }
    
    // Parseo optimizado de string de vector
    private static int[] parseVectorString(String vectorStr) {
        // Eliminar corchetes y espacios
        String clean = vectorStr.replaceAll("[\\[\\] ]", "");
        if (clean.isEmpty()) return new int[0];
        
        String[] parts = clean.split(",");
        int[] result = new int[parts.length];
        
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i]);
        }
        
        return result;
    }
}