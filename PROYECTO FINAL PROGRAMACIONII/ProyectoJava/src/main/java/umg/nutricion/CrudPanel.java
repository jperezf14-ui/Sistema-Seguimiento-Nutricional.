package umg.nutricion;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import java.awt.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.regex.*;

public final class CrudPanel extends JPanel {
    record Campo(String nombre,String tipo,boolean nullable,String defecto,boolean autoinc,boolean generado){}
    record Opcion(Object id,String nombre){public String toString(){return nombre;}}
    private final String tabla,pk;
    private final DB.Sesion sesion;
    private final boolean escribir;
    private final List<Campo> campos=new ArrayList<>();
    private final Map<String,String> referencias=new HashMap<>();
    private final DefaultTableModel modelo=new DefaultTableModel(){public boolean isCellEditable(int row,int col){return false;}};
    private final JTable grilla=new JTable(modelo);
    private final JTextField buscar=new JTextField(18);
    private final JLabel info=new JLabel(" ");
    public CrudPanel(String tabla,DB.Sesion sesion,boolean escribir) throws SQLException {
        super(new BorderLayout(8,8));
        if(!tabla.matches("[a-z_]+"))throw new IllegalArgumentException("Tabla inválida");
        this.tabla=tabla;this.sesion=sesion;this.escribir=escribir;
        try(Statement st=DB.conn.createStatement();ResultSet r=st.executeQuery("SHOW FULL COLUMNS FROM `"+tabla+"`")){
            while(r.next()){
                String nombre=r.getString("Field"),extra=r.getString("Extra");
                campos.add(new Campo(nombre,r.getString("Type"),"YES".equals(r.getString("Null")),r.getString("Default"),extra.contains("auto_increment"),extra.contains("GENERATED")));
            }
        }
        pk=campos.get(0).nombre;
        try(ResultSet rs=DB.conn.getMetaData().getImportedKeys(DB.conn.getCatalog(),null,tabla)){
            while(rs.next()) referencias.put(rs.getString("FKCOLUMN_NAME"),rs.getString("PKTABLE_NAME"));
        }
        // En FK compuestas id_familia debe abrir Familias, no Niños.
        if(referencias.containsKey("id_familia"))referencias.put("id_familia","familia");
        if(referencias.containsKey("id_nino"))referencias.put("id_nino","nino");
        JPanel arriba=new JPanel(new FlowLayout(FlowLayout.LEFT));
        if(escribir){
            JButton nuevo=btn("Nuevo registro",()->editar(null));
            JButton editar=btn("Modificar",()->{Object id=seleccion();if(id!=null)editar(id);});
            JButton borrar=btn("Eliminar",()->borrar());
            arriba.add(nuevo);arriba.add(editar);arriba.add(borrar);
        }
        arriba.add(btn("Actualizar",()->recargar()));
        arriba.add(new JLabel("Buscar:"));arriba.add(buscar);
        add(arriba,BorderLayout.NORTH);
        grilla.setAutoCreateRowSorter(true);grilla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        grilla.setRowHeight(25);grilla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        add(new JScrollPane(grilla),BorderLayout.CENTER);
        add(info,BorderLayout.SOUTH);
        buscar.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filtrar();}public void removeUpdate(DocumentEvent e){filtrar();}public void changedUpdate(DocumentEvent e){filtrar();}});
        recargar();
    }
    private JButton btn(String label,Runnable r){JButton b=new JButton(label);b.addActionListener(e->{try{r.run();}catch(Exception x){error(x);}});return b;}
    private void error(Exception e){JOptionPane.showMessageDialog(this,"Operación no realizada:\n"+e.getMessage(),"Aviso",JOptionPane.WARNING_MESSAGE);}
    private void filtrar(){
        if(grilla.getRowSorter() instanceof TableRowSorter<?> sorter){
            String valor=buscar.getText().trim();
            try{sorter.setRowFilter(valor.isEmpty()?null:RowFilter.regexFilter("(?i)"+Pattern.quote(valor)));}catch(Exception e){/* no filtrar */}
        }
    }
    private Object seleccion(){
        int i=grilla.getSelectedRow();
        if(i<0){JOptionPane.showMessageDialog(this,"Primero selecciona una fila.");return null;}
        return modelo.getValueAt(grilla.convertRowIndexToModel(i),0);
    }
    public void recargar(){
        modelo.setRowCount(0);
        ArrayList<String> cols=new ArrayList<>();
        for(Campo c:campos) if(!c.nombre.equals("clave_hash")&&!c.nombre.equals("logotipo")&&!c.generado)cols.add(c.nombre);
        // Siempre PK es primera columna en la definición SQL.
        modelo.setColumnIdentifiers(cols.toArray());
        String sql="SELECT "+String.join(",",cols)+" FROM `"+tabla+"` ORDER BY `"+pk+"` DESC LIMIT 500";
        try(Statement st=DB.conn.createStatement();ResultSet r=st.executeQuery(sql)){
            while(r.next()){
                Object[] x=new Object[cols.size()];for(int i=0;i<cols.size();i++)x[i]=r.getObject(i+1);
                modelo.addRow(x);
            }
            for(int i=0;i<grilla.getColumnCount();i++)grilla.getColumnModel().getColumn(i).setPreferredWidth(i==0?75:160);
            info.setText("Tabla: "+tabla+" | "+modelo.getRowCount()+" filas (máximo 500). Selecciona una fila para modificarla.");
            filtrar();
        }catch(Exception e){info.setText("Error: "+e.getMessage());}
    }
    private void borrar(){
        Object id=seleccion();if(id==null)return;
        if(Set.of("control_nutricional","evaluacion_seguimiento","movimiento_inventario","entrega_ayuda").contains(tabla)){
            JOptionPane.showMessageDialog(this,"Por seguridad este módulo conserva el historial. Cambia el estado cuando corresponda.");return;
        }
        try{
            if(tabla.equals("detalle_entrega"))revisarDetalle(id);
            if(tabla.equals("lote_ayuda")) throw new SQLException("No se elimina un lote con historial. Bloquéalo si ya no debe utilizarse.");
            if(JOptionPane.showConfirmDialog(this,"¿Eliminar el registro "+id+" de "+tabla+"?", "Confirmar",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            try(PreparedStatement p=DB.conn.prepareStatement("DELETE FROM `"+tabla+"` WHERE `"+pk+"`=?")){p.setObject(1,id);p.executeUpdate();}
            recargar();
        }catch(Exception e){error(e);}
    }
    private void revisarDetalle(Object id) throws SQLException {
        String estado=(String)DB.scalar("SELECT e.estado FROM entrega_ayuda e JOIN detalle_entrega d ON d.id_entrega=e.id_entrega WHERE d.id_detalle=?",id);
        if(!"Borrador".equals(estado))throw new SQLException("Solo puedes editar detalles de entregas en Borrador.");
    }
    private boolean protegido(Object id) throws SQLException {
        if(id==null)return false;
        if(tabla.equals("detalle_entrega"))revisarDetalle(id);
        if(tabla.equals("entrega_ayuda")){
            Object st=DB.scalar("SELECT estado FROM entrega_ayuda WHERE id_entrega=?",id);
            if(!"Borrador".equals(st))throw new SQLException("Entrega confirmada: no puede modificarse.");
        }
        if(tabla.equals("lote_ayuda"))throw new SQLException("Los lotes existentes solo cambian con movimientos controlados (Existencias).");
        return true;
    }
    private void editar(Object id){
        try{
            protegido(id);
            Map<String,Object> anterior=new HashMap<>();
            if(id!=null)try(PreparedStatement p=DB.conn.prepareStatement("SELECT * FROM `"+tabla+"` WHERE `"+pk+"`=?")){
                p.setObject(1,id);try(ResultSet rs=p.executeQuery()){if(rs.next())for(Campo c:campos)anterior.put(c.nombre,rs.getObject(c.nombre));}
            }
            JPanel form=new JPanel(new GridBagLayout());
            JScrollPane scroll=new JScrollPane(form);scroll.setPreferredSize(new Dimension(660,Math.min(540,Math.max(180,campos.size()*37))));
            Map<String,JComponent> entradas=new LinkedHashMap<>();
            int fila=0;
            for(Campo c:campos){
                if(c.nombre.equals(pk)||c.autoinc||c.generado||c.nombre.equals("logotipo")||c.nombre.equals("existencia_actual")||c.nombre.equals("fecha_confirmacion")||c.nombre.equals("fecha_creacion")||c.nombre.equals("fecha_registro")&&tabla.equals("cita")||c.nombre.equals("fecha_movimiento")||c.nombre.equals("edad_meses")&&tabla.equals("control_nutricional"))continue;
                if(c.nombre.equals("estado")&&tabla.equals("entrega_ayuda"))continue;
                if(c.nombre.equals("fecha_autorizacion")&&tabla.equals("detalle_entrega"))continue;
                JComponent componente;
                if(c.nombre.equals("clave_hash"))componente=new JPasswordField(26);
                else if(referencias.containsKey(c.nombre))componente=cargarFK(referencias.get(c.nombre),c.nullable);
                else if(c.tipo.startsWith("enum(")){
                    JComboBox<String> cb=new JComboBox<>();
                    if(c.nullable)cb.addItem("");
                    Matcher m=Pattern.compile("'((?:[^']|'')*)'").matcher(c.tipo);
                    while(m.find())cb.addItem(m.group(1).replace("''","'"));
                    componente=cb;
                }else componente=new JTextField(30);
                Object previo=anterior.get(c.nombre);
                if(componente instanceof JTextField t){
                    if(c.nombre.equals("clave_hash"))t.setText("");
                    else if(previo!=null)t.setText(previo.toString());
                    else if(c.defecto!=null && !c.defecto.equalsIgnoreCase("CURRENT_TIMESTAMP"))t.setText(c.defecto);
                    else if(c.tipo.equals("date")&&c.nombre.contains("fecha"))t.setText(LocalDate.now().toString());
                    else if((c.tipo.equals("datetime")||c.tipo.equals("timestamp"))&&c.nombre.contains("fecha"))t.setText(java.time.LocalDateTime.now().withNano(0).toString().replace('T',' '));
                    else if(c.nombre.equals("id_usuario_registra")||c.nombre.equals("id_usuario_entrega")||c.nombre.equals("id_promotor")||c.nombre.equals("id_profesional")||c.nombre.equals("id_responsable_seguimiento"))t.setText(String.valueOf(sesion.id()));
                } else if(componente instanceof JComboBox<?> cb){
                    Object valor=previo!=null?previo:c.defecto;
                    if(valor!=null)for(int k=0;k<cb.getItemCount();k++){
                        Object op=cb.getItemAt(k);
                        if(op instanceof Opcion v && String.valueOf(v.id).equals(String.valueOf(valor)) || String.valueOf(op).equals(String.valueOf(valor))){cb.setSelectedIndex(k);break;}
                    }
                }
                GridBagConstraints gc=new GridBagConstraints();gc.gridy=fila;gc.gridx=0;gc.insets=new Insets(4,8,4,8);gc.anchor=GridBagConstraints.WEST;
                form.add(new JLabel(c.nombre+(c.nullable?"":" *")),gc);
                gc.gridx=1;gc.weightx=1;gc.fill=GridBagConstraints.HORIZONTAL;form.add(componente,gc);
                entradas.put(c.nombre,componente);fila++;
            }
            if(JOptionPane.showConfirmDialog(this,scroll,(id==null?"Registrar en ":"Modificar en ")+tabla,JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
            Map<String,Object> valores=new LinkedHashMap<>();
            for(Campo c:campos){
                JComponent comp=entradas.get(c.nombre);if(comp==null)continue;
                Object valor=null;
                if(comp instanceof JComboBox<?> cb){Object s=cb.getSelectedItem();valor=s instanceof Opcion o?o.id:s;}
                if(comp instanceof JPasswordField p){char[] chars=p.getPassword(); if(chars.length>0){if(chars.length<8)throw new Exception("La contraseña debe tener al menos 8 caracteres");valor=Passwords.hash(chars);}}
                else if(comp instanceof JTextField t)valor=t.getText().trim();
                if(valor!=null&&valor.toString().isBlank())valor=null;
                if(c.nombre.equals("clave_hash")&&id!=null&&valor==null)continue;
                if(valor==null&&!c.nullable&&c.defecto==null)throw new SQLException("Campo obligatorio: "+c.nombre);
                valores.put(c.nombre,valor);
            }
            // Los valores gestionados internamente se fijan sin permitir modificarlos desde el formulario.
            if(tabla.equals("entrega_ayuda")&&id==null)valores.put("estado","Borrador");
            if(tabla.equals("detalle_entrega")&&valores.get("id_usuario_autoriza")!=null)valores.put("fecha_autorizacion",java.time.LocalDateTime.now().withNano(0).toString().replace('T',' '));
            if(tabla.equals("control_nutricional"))validarControl(valores);
            if(tabla.equals("nino"))validarNino(valores);
            if(tabla.equals("evaluacion_seguimiento"))validarEvaluacion(valores);
            if(tabla.equals("cita")&&"Programada".equals(String.valueOf(valores.get("estado"))))validarCita(valores);
            if(tabla.equals("lote_ayuda")&&id==null)valores.put("existencia_actual",0);
            if(id==null)insertar(valores);else actualizar(id,valores);
            recargar();JOptionPane.showMessageDialog(this,"Registro guardado correctamente.");
        }catch(Exception e){error(e);}
    }
    private void validarNino(Map<String,Object> v) throws Exception {
        Object f=v.get("fecha_nacimiento");if(f!=null && LocalDate.parse(f.toString()).isAfter(LocalDate.now()))throw new Exception("Fecha de nacimiento futura.");
    }
    private void validarControl(Map<String,Object> v) throws Exception {
        LocalDate fecha=LocalDate.parse(String.valueOf(v.get("fecha_control")));
        if(fecha.isAfter(LocalDate.now()))throw new Exception("No se pueden registrar controles futuros.");
        Object nac=DB.scalar("SELECT fecha_nacimiento FROM nino WHERE id_nino=?",v.get("id_nino"));
        if(nac==null)throw new Exception("Selecciona un niño válido.");
        LocalDate nacimiento=((java.sql.Date)nac).toLocalDate();
        if(fecha.isBefore(nacimiento))throw new Exception("Fecha del control anterior al nacimiento.");
        v.put("edad_meses",ChronoUnit.MONTHS.between(nacimiento,fecha));
        Object rol=DB.scalar("SELECT r.nombre FROM usuario u JOIN rol r ON u.id_rol=r.id_rol WHERE u.id_usuario=? AND u.estado='Activo'",v.get("id_profesional"));
        if(!"Profesional de salud o nutrición".equals(rol))throw new Exception("El control requiere un profesional de salud autorizado.");
    }
    private void validarEvaluacion(Map<String,Object> v) throws Exception {
        Object rol=DB.scalar("SELECT r.nombre FROM usuario u JOIN rol r ON r.id_rol=u.id_rol WHERE u.id_usuario=? AND u.estado='Activo'",v.get("id_responsable_seguimiento"));
        if(!"Profesional de salud o nutrición".equals(rol))throw new Exception("La evaluación requiere responsable profesional autorizado.");
    }
    private void validarCita(Map<String,Object> v) throws Exception {
        if(v.get("fecha_cita")==null||v.get("hora_cita")==null)throw new Exception("La fecha y hora son obligatorias.");
    }
    private void insertar(Map<String,Object> valores) throws SQLException {
        String col=String.join(",",valores.keySet());String ph=String.join(",",Collections.nCopies(valores.size(),"?"));
        try(PreparedStatement p=DB.conn.prepareStatement("INSERT INTO `"+tabla+"` ("+col+") VALUES ("+ph+")")){
            DB.bind(p,valores.values().toArray());p.executeUpdate();
        }
    }
    private void actualizar(Object id,Map<String,Object> valores) throws SQLException {
        if(valores.isEmpty())return;
        StringJoiner sj=new StringJoiner(",");for(String c:valores.keySet())sj.add("`"+c+"`=?");
        try(PreparedStatement p=DB.conn.prepareStatement("UPDATE `"+tabla+"` SET "+sj+" WHERE `"+pk+"`=?")){
            ArrayList<Object> args=new ArrayList<>(valores.values());args.add(id);DB.bind(p,args.toArray());p.executeUpdate();
        }
    }
    private JComboBox<Opcion> cargarFK(String referencia,boolean nullable) throws SQLException {
        JComboBox<Opcion> combo=new JComboBox<>();
        if(nullable)combo.addItem(new Opcion(null,"(Sin seleccionar)"));
        String titulo=switch(referencia){
            case "usuario" -> "nombre_completo"; case "familia" -> "nombre_responsable";
            case "nino" -> "nombres"; case "rol","producto_ayuda" -> "nombre";
            case "lote_ayuda" -> "codigo_lote";case "entrega_ayuda" -> "numero_entrega";
            case "control_nutricional" -> "numero_control";
            case "detalle_entrega" -> "id_detalle";
            default -> "id_"+referencia;
        };
        String id="id_"+switch(referencia){case "producto_ayuda"->"producto";case "lote_ayuda"->"lote";case "entrega_ayuda"->"entrega";case "control_nutricional"->"control";case "detalle_entrega"->"detalle";default->referencia;};
        if(referencia.equals("rol"))id="id_rol";
        try(Statement s=DB.conn.createStatement();ResultSet r=s.executeQuery("SELECT `"+id+"`,`"+titulo+"` FROM `"+referencia+"` ORDER BY `"+id+"` DESC LIMIT 1000")){
            while(r.next())combo.addItem(new Opcion(r.getObject(1),r.getString(1)+" - "+r.getString(2)));
        }
        return combo;
    }
}
