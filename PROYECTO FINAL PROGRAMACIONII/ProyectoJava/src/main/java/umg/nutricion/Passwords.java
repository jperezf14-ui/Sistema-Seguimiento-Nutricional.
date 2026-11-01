package umg.nutricion;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Passwords {
    private Passwords(){}
    private static final int ITERACIONES=210_000;
    public static String hash(char[] password) throws Exception {
        byte[] sal=new byte[16];new SecureRandom().nextBytes(sal);
        byte[] derivada=derivar(password,sal,ITERACIONES);
        Arrays.fill(password,'\0');
        return "pbkdf2$"+ITERACIONES+"$"+Base64.getEncoder().encodeToString(sal)+"$"+Base64.getEncoder().encodeToString(derivada);
    }
    public static boolean verificar(char[] password,String guardado) throws Exception {
        try {
            String[] p=guardado.split("\\$");
            if(p.length!=4 || !p[0].equals("pbkdf2"))return false;
            int iter=Integer.parseInt(p[1]);
            byte[] expected=Base64.getDecoder().decode(p[3]);
            byte[] actual=derivar(password,Base64.getDecoder().decode(p[2]),iter);
            return MessageDigest.isEqual(expected,actual);
        } finally {Arrays.fill(password,'\0');}
    }
    private static byte[] derivar(char[] pass,byte[] sal,int vueltas) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(pass,sal,vueltas,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        finally{spec.clearPassword();}
    }
}
