package umg.nutricion;

import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.swing.*;

public final class Inventario {
    private Inventario(){}
    public static void entrada(DB.Sesion sesion) throws Exception {
        if(!sesion.admin())throw new Exception("Solo el administrador puede registrar entradas de inventario.");
        JTextField lote=new JTextField(),cantidad=new JTextField(),motivo=new JTextField("Ingreso de productos");
        if(JOptionPane.showConfirmDialog(null,new Object[]{"ID del lote (consulta la pestaña Lotes):",lote,"Cantidad recibida:",cantidad,"Motivo:",motivo},"Registrar entrada de inventario",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        int id=Integer.parseInt(lote.getText().trim());BigDecimal q=new BigDecimal(cantidad.getText().trim());
        if(q.signum()<=0)throw new Exception("Cantidad debe ser mayor que cero.");
        if(motivo.getText().isBlank())throw new Exception("El motivo es obligatorio.");
        boolean auto=DB.conn.getAutoCommit();
        try {
            DB.conn.setAutoCommit(false);
            try(PreparedStatement p=DB.conn.prepareStatement("SELECT estado FROM lote_ayuda WHERE id_lote=? FOR UPDATE")){
                p.setInt(1,id);try(ResultSet r=p.executeQuery()){
                    if(!r.next())throw new Exception("Lote inexistente.");
                    if(!"Activo".equals(r.getString(1)))throw new Exception("Lote bloqueado.");
                }
            }
            try(PreparedStatement p=DB.conn.prepareStatement("UPDATE lote_ayuda SET existencia_actual=existencia_actual+? WHERE id_lote=?")){
                p.setBigDecimal(1,q);p.setInt(2,id);p.executeUpdate();
            }
            try(PreparedStatement p=DB.conn.prepareStatement("INSERT INTO movimiento_inventario(id_lote,id_usuario,tipo_movimiento,cantidad,motivo) VALUES (?,?,'Entrada',?,?)")){
                p.setInt(1,id);p.setInt(2,sesion.id());p.setBigDecimal(3,q);p.setString(4,motivo.getText().trim());p.executeUpdate();
            }
            DB.conn.commit();JOptionPane.showMessageDialog(null,"Entrada registrada y existencia actualizada.");
        }catch(Exception e){DB.conn.rollback();throw e;}
        finally{DB.conn.setAutoCommit(auto);}
    }
    public static void confirmar(DB.Sesion sesion) throws Exception {
        if(!(sesion.admin()||sesion.promotor()))throw new Exception("Tu rol no permite confirmar entregas.");
        String texto=JOptionPane.showInputDialog(null,"ID de la entrega que está en Borrador:","Confirmar entrega",JOptionPane.QUESTION_MESSAGE);
        if(texto==null)return;
        int id=Integer.parseInt(texto.trim());
        boolean auto=DB.conn.getAutoCommit();
        try {
            DB.conn.setAutoCommit(false);
            try(PreparedStatement p=DB.conn.prepareStatement("SELECT estado FROM entrega_ayuda WHERE id_entrega=? FOR UPDATE")){
                p.setInt(1,id);try(ResultSet r=p.executeQuery()){
                    if(!r.next())throw new Exception("No existe esa entrega.");
                    if(!"Borrador".equals(r.getString(1)))throw new Exception("La entrega debe estar en Borrador.");
                }
            }
            String sql="SELECT d.id_detalle,d.id_lote,d.cantidad,d.id_usuario_autoriza,d.fecha_autorizacion,"
                +" l.existencia_actual,l.fecha_vencimiento,l.estado,p.estado,p.tipo "
                +"FROM detalle_entrega d JOIN lote_ayuda l ON l.id_lote=d.id_lote "
                +"JOIN producto_ayuda p ON p.id_producto=l.id_producto "
                +"WHERE d.id_entrega=? ORDER BY d.id_lote FOR UPDATE";
            int lineas=0;
            try(PreparedStatement p=DB.conn.prepareStatement(sql)){
                p.setInt(1,id);try(ResultSet r=p.executeQuery()){
                    while(r.next()){
                        lineas++;
                        BigDecimal q=r.getBigDecimal("cantidad"),disponible=r.getBigDecimal("existencia_actual");
                        java.sql.Date venc=r.getDate("fecha_vencimiento");
                        if(q.signum()<=0||disponible.compareTo(q)<0)throw new Exception("Sin existencias suficientes para lote "+r.getInt("id_lote"));
                        if(venc!=null&&venc.toLocalDate().isBefore(LocalDate.now()))throw new Exception("Lote vencido: "+r.getInt("id_lote"));
                        if(!"Activo".equals(r.getString(8))||!"Activo".equals(r.getString(9)))throw new Exception("Lote o producto inactivo.");
                        if("Suplemento autorizado".equals(r.getString("tipo"))){
                            int autorizador=r.getInt("id_usuario_autoriza");
                            if(r.wasNull()||r.getTimestamp("fecha_autorizacion")==null)throw new Exception("Suplemento sin autorización profesional.");
                            Object rol=DB.scalar("SELECT ro.nombre FROM usuario u JOIN rol ro ON u.id_rol=ro.id_rol WHERE u.id_usuario=? AND u.estado='Activo'",autorizador);
                            if(!"Profesional de salud o nutrición".equals(rol))throw new Exception("El suplemento necesita autorización de un profesional de salud activo.");
                        }
                        try(PreparedStatement upd=DB.conn.prepareStatement("UPDATE lote_ayuda SET existencia_actual=existencia_actual-? WHERE id_lote=? AND existencia_actual>=?")){
                            upd.setBigDecimal(1,q);upd.setInt(2,r.getInt("id_lote"));upd.setBigDecimal(3,q);
                            if(upd.executeUpdate()!=1)throw new Exception("Existencia insuficiente o cambio simultáneo.");
                        }
                        try(PreparedStatement mov=DB.conn.prepareStatement("INSERT INTO movimiento_inventario(id_lote,id_detalle_entrega,id_usuario,tipo_movimiento,cantidad,motivo) VALUES (?,?,?,'Salida',?,'Entrega confirmada')")){
                            mov.setInt(1,r.getInt("id_lote"));mov.setInt(2,r.getInt("id_detalle"));mov.setInt(3,sesion.id());mov.setBigDecimal(4,q);mov.executeUpdate();
                        }
                    }
                }
            }
            if(lineas==0)throw new Exception("La entrega no tiene productos. Registra primero sus detalles.");
            try(PreparedStatement p=DB.conn.prepareStatement("UPDATE entrega_ayuda SET estado='Confirmada',fecha_confirmacion=NOW() WHERE id_entrega=?")){
                p.setInt(1,id);p.executeUpdate();
            }
            DB.conn.commit();JOptionPane.showMessageDialog(null,"Entrega confirmada. Inventario descontado y movimientos registrados.");
        }catch(Exception e){DB.conn.rollback();throw e;}
        finally{DB.conn.setAutoCommit(auto);}
    }
}
