package umg.nutricion;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** PDF A4 basico de texto, sin bibliotecas externas; apropiado para informes de prueba. */
public final class PdfSimple {
    private static final Charset CP=Charset.forName("windows-1252");
    private PdfSimple(){}
    private static String escapar(String s){
        return s.replace('\\','/').replace("(","[").replace(")","]").replace('\n',' ').replace('\r',' ');
    }
    public static void guardar(File archivo,String titulo,List<String> lineas) throws IOException {
        List<String> trozos=new ArrayList<>();
        trozos.add(titulo);trozos.add("Fecha de generacion: "+java.time.LocalDateTime.now().withNano(0));trozos.add(" ");
        for(String l:lineas){
            if(l==null)l="";
            while(l.length()>90){int corte=Math.min(90,l.length());int espacio=l.lastIndexOf(' ',corte);
                if(espacio>20)corte=espacio;
                trozos.add(l.substring(0,corte));l=l.substring(corte).stripLeading();
            }
            trozos.add(l);
        }
        int porPagina=45;
        int n=Math.max(1,(int)Math.ceil(trozos.size()/(double)porPagina));
        List<byte[]> objetos=new ArrayList<>();
        objetos.add(null); // indices comienzan en 1
        objetos.add(b("<< /Type /Catalog /Pages 2 0 R >>"));
        StringBuilder kids=new StringBuilder();for(int i=0;i<n;i++)kids.append(4+i*2).append(" 0 R ");
        objetos.add(b("<< /Type /Pages /Kids ["+kids+"] /Count "+n+" >>"));
        objetos.add(b("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        for(int page=0;page<n;page++){
            int paginaId=objetos.size();int streamId=paginaId+1;
            objetos.add(b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents "+streamId+" 0 R >>"));
            StringBuilder contenido=new StringBuilder();
            for(int i=page*porPagina;i<Math.min(trozos.size(),(page+1)*porPagina);i++){
                int linea=i-page*porPagina;int y=785-linea*16;
                String s=escapar(trozos.get(i));
                contenido.append("BT /F1 ").append(linea==0?14:10).append(" Tf 44 ").append(y).append(" Td (").append(s).append(") Tj ET\n");
            }
            contenido.append("BT /F1 9 Tf 490 35 Td (Pagina ").append(page+1).append(" / ").append(n).append(") Tj ET\n");
            byte[] bytes=b(contenido.toString());
            ByteArrayOutputStream ob=new ByteArrayOutputStream();
            ob.write(b("<< /Length "+bytes.length+" >>\nstream\n"));ob.write(bytes);ob.write(b("\nendstream"));
            objetos.add(ob.toByteArray());
        }
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        int[] offsets=new int[objetos.size()];
        for(int i=1;i<objetos.size();i++){
            offsets[i]=out.size();out.write(b(i+" 0 obj\n"));out.write(objetos.get(i));out.write(b("\nendobj\n"));
        }
        int xref=out.size();out.write(b("xref\n0 "+objetos.size()+"\n0000000000 65535 f \n"));
        for(int i=1;i<objetos.size();i++)out.write(b(String.format(Locale.ROOT,"%010d 00000 n \n",offsets[i])));
        out.write(b("trailer\n<< /Size "+objetos.size()+" /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n"));
        try(FileOutputStream f=new FileOutputStream(archivo)){out.writeTo(f);}
    }
    private static byte[] b(String s){return s.getBytes(CP);}
}
