package umg.nutricion;

import java.sql.*;
import java.io.File;
import java.util.*;
import javax.swing.*;

public final class Documentos {
    private Documentos(){}
    private static List<String> institucion() throws SQLException {
        List<String> l=new ArrayList<>();
        try(Statement st=DB.conn.createStatement();ResultSet r=st.executeQuery("SELECT nombre,codigo_institucional,direccion,telefono,correo,nombre_responsable FROM institucion LIMIT 1")){
            if(r.next()){l.add("Institucion: "+r.getString(1)+" - Codigo: "+r.getString(2));l.add("Direccion: "+r.getString(3)+" | Telefono: "+r.getString(4));l.add("Correo: "+r.getString(5)+" | Responsable: "+r.getString(6));}
            else l.add("INSTITUCION NO CONFIGURADA: registrar datos antes de entregar el documento.");
        }
        l.add(" ");return l;
    }
    public static void ficha() throws Exception {
        String s=JOptionPane.showInputDialog(null,"ID del niño:","Ficha nutricional",JOptionPane.QUESTION_MESSAGE);
        if(s==null)return;int id=Integer.parseInt(s.trim());
        List<String> lineas=institucion();
        try(PreparedStatement p=DB.conn.prepareStatement("SELECT n.codigo,n.nombres,n.apellidos,n.fecha_nacimiento,n.estado_seguimiento,f.nombre_responsable,f.telefono FROM nino n JOIN familia f ON f.id_familia=n.id_familia WHERE n.id_nino=?")){
            p.setInt(1,id);try(ResultSet r=p.executeQuery()){
                if(!r.next())throw new Exception("No existe un niño con ID "+id);
                lineas.add("Nino: "+r.getString(2)+" "+r.getString(3)+" | Codigo: "+r.getString(1));
                lineas.add("Nacimiento: "+r.getString(4)+" | Estado: "+r.getString(5));
                lineas.add("Responsable familiar: "+r.getString(6)+" | Tel: "+r.getString(7));
            }
        }
        lineas.add(" ");lineas.add("CONTROLES NUTRICIONALES (orden cronologico)");
        try(PreparedStatement p=DB.conn.prepareStatement("SELECT c.numero_control,c.fecha_control,c.peso_kg,c.talla_cm,c.perimetro_braquial_cm,c.clasificacion_nutricional,u.nombre_completo,c.proximo_control,c.observaciones,e.recomendaciones FROM control_nutricional c JOIN usuario u ON u.id_usuario=c.id_profesional LEFT JOIN evaluacion_seguimiento e ON e.id_control=c.id_control WHERE c.id_nino=? ORDER BY c.fecha_control,c.numero_control")){
            p.setInt(1,id);try(ResultSet r=p.executeQuery()){
                while(r.next()){
                    lineas.add("Control "+r.getString(1)+" - "+r.getString(2)+" | Peso: "+r.getString(3)+" kg | Talla: "+r.getString(4)+" cm | Brazo: "+r.getString(5));
                    lineas.add("Clasificacion (profesional): "+r.getString(6)+" | Responsable: "+r.getString(7));
                    lineas.add("Proximo control: "+r.getString(8)+" | Observaciones: "+r.getString(9));
                    if(r.getString(10)!=null)lineas.add("Recomendaciones: "+r.getString(10));
                    lineas.add(" ");
                }
            }
        }
        try(PreparedStatement p=DB.conn.prepareStatement("SELECT fecha_cita,hora_cita,lugar_atencion FROM cita WHERE id_nino=? AND estado='Programada' ORDER BY fecha_cita,hora_cita LIMIT 1")){
            p.setInt(1,id);try(ResultSet r=p.executeQuery()){if(r.next())lineas.add("Proxima cita: "+r.getString(1)+" "+r.getString(2)+" en "+r.getString(3));}
        }
        lineas.add("Documento administrativo; no reemplaza una evaluacion profesional de salud.");
        guardar("FichaNutricional.pdf","FICHA DE SEGUIMIENTO NUTRICIONAL",lineas);
    }
    public static void constancia() throws Exception {
        String s=JOptionPane.showInputDialog(null,"ID de entrega confirmada:","Constancia de entrega",JOptionPane.QUESTION_MESSAGE);
        if(s==null)return;int id=Integer.parseInt(s.trim());
        List<String> l=institucion();
        try(PreparedStatement p=DB.conn.prepareStatement("SELECT e.numero_entrega,e.fecha_entrega,e.estado,n.nombres,n.apellidos,f.nombre_responsable,u.nombre_completo,e.motivo,e.observaciones FROM entrega_ayuda e JOIN nino n ON n.id_nino=e.id_nino JOIN familia f ON f.id_familia=e.id_familia JOIN usuario u ON u.id_usuario=e.id_usuario_entrega WHERE e.id_entrega=?")){
            p.setInt(1,id);try(ResultSet r=p.executeQuery()){
                if(!r.next())throw new Exception("Entrega inexistente.");
                if(!"Confirmada".equals(r.getString(3)))throw new Exception("Solo se emiten constancias de entregas confirmadas.");
                l.add("No. de entrega: "+r.getString(1)+" | Fecha: "+r.getString(2));
                l.add("Nino beneficiado: "+r.getString(4)+" "+r.getString(5));l.add("Responsable familiar: "+r.getString(6));
                l.add("Entregado por: "+r.getString(7));l.add("Motivo: "+r.getString(8));l.add("Observaciones: "+r.getString(9));
            }
        }
        l.add(" ");l.add("PRODUCTOS ENTREGADOS:");
        try(PreparedStatement p=DB.conn.prepareStatement("SELECT p.nombre,l.codigo_lote,d.cantidad,p.unidad_medida FROM detalle_entrega d JOIN lote_ayuda l ON l.id_lote=d.id_lote JOIN producto_ayuda p ON p.id_producto=l.id_producto WHERE d.id_entrega=? ORDER BY p.nombre")){
            p.setInt(1,id);try(ResultSet r=p.executeQuery()){while(r.next())l.add(r.getString(1)+" | Lote "+r.getString(2)+" | "+r.getString(3)+" "+r.getString(4));}
        }
        l.add(" ");l.add("Firma de entrega: ____________________    Firma del responsable: ____________________");
        guardar("ConstanciaEntrega.pdf","CONSTANCIA DE ENTREGA DE AYUDA",l);
    }
    private static void guardar(String sugerido,String titulo,List<String> datos) throws Exception {
        JFileChooser c=new JFileChooser();c.setSelectedFile(new File(sugerido));
        if(c.showSaveDialog(null)!=JFileChooser.APPROVE_OPTION)return;
        PdfSimple.guardar(c.getSelectedFile(),titulo,datos);
        JOptionPane.showMessageDialog(null,"PDF generado: "+c.getSelectedFile().getAbsolutePath());
    }
}
