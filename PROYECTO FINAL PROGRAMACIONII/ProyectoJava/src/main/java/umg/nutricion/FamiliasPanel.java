package umg.nutricion;

import java.awt.*;
import java.time.LocalDate;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.event.*;

/** Pantalla CRUD de familias con modelo Familia + FamiliaDAO (ejemplo POO para la defensa). */
public final class FamiliasPanel extends JPanel {
    private final FamiliaDAO dao=new FamiliaDAO(DB.conn);
    private final boolean editable;
    private final DefaultTableModel model=new DefaultTableModel(new String[]{"ID","Código","Responsable","Identificación","Comunidad","Municipio","Departamento","Integrantes","Estado"},0){public boolean isCellEditable(int r,int c){return false;}};
    private final JTable tabla=new JTable(model);
    private final JTextField buscar=new JTextField(18);
    public FamiliasPanel(boolean editable){
        super(new BorderLayout(6,6));this.editable=editable;
        JPanel arriba=new JPanel(new FlowLayout(FlowLayout.LEFT));
        if(editable){arriba.add(boton("Crear",()->formulario(null)));arriba.add(boton("Modificar",()->{Integer id=idSeleccionado();if(id==null)throw new Exception("Selecciona una familia para modificarla.");formulario(id);}));arriba.add(boton("Eliminar",()->eliminar()));}
        arriba.add(boton("Consultar / actualizar",()->cargar()));arriba.add(new JLabel("Buscar:"));arriba.add(buscar);
        add(arriba,BorderLayout.NORTH);tabla.setAutoCreateRowSorter(true);tabla.setRowHeight(26);add(new JScrollPane(tabla),BorderLayout.CENTER);
        buscar.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filtrar();}public void removeUpdate(DocumentEvent e){filtrar();}public void changedUpdate(DocumentEvent e){filtrar();}});
        cargar();
    }
    private interface Accion{void run()throws Exception;}
    private JButton boton(String titulo,Accion accion){JButton b=new JButton(titulo);b.addActionListener(e->{try{accion.run();}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Aviso",JOptionPane.WARNING_MESSAGE);}});return b;}
    private Integer idSeleccionado(){int fila=tabla.getSelectedRow();return fila<0?null:((Number)model.getValueAt(tabla.convertRowIndexToModel(fila),0)).intValue();}
    private void cargar(){
        try {model.setRowCount(0);for(Familia f:dao.listar())model.addRow(new Object[]{f.getId(),f.getCodigo(),f.getResponsable(),f.getIdentificacion(),f.getComunidad(),f.getMunicipio(),f.getDepartamento(),f.getIntegrantes(),f.getEstado()});}
        catch(SQLException e){JOptionPane.showMessageDialog(this,"Error consultando familias: "+e.getMessage());}
    }
    private void filtrar(){if(tabla.getRowSorter() instanceof TableRowSorter<?> s){String q=buscar.getText().trim();s.setRowFilter(q.isEmpty()?null:RowFilter.regexFilter("(?i)"+java.util.regex.Pattern.quote(q)));}}
    private void eliminar()throws Exception{
        Integer id=idSeleccionado();if(id==null)throw new Exception("Selecciona una familia.");
        if(JOptionPane.showConfirmDialog(this,"Eliminar familia "+id+" (solo si no tiene niños ni historial)","Confirmar",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        dao.eliminar(id);cargar();
    }
    private void formulario(Integer id)throws Exception {
        Familia anterior=id==null?null:dao.buscar(id);
        if(id!=null&&anterior==null)throw new Exception("Familia no encontrada.");
        String[] etiquetas={"Código","Nombre del responsable","Número de identificación","Dirección","Comunidad","Municipio","Departamento","Teléfono","Integrantes","Condiciones del hogar","Fecha registro (AAAA-MM-DD)","Estado","Observaciones"};
        JTextField[] campos=new JTextField[etiquetas.length];JPanel p=new JPanel(new GridLayout(etiquetas.length,2,8,5));
        String[] valores=anterior==null?new String[]{"","","","","","","","","1","",LocalDate.now().toString(),"Activa",""}:
            new String[]{anterior.getCodigo(),anterior.getResponsable(),anterior.getIdentificacion(),anterior.getDireccion(),anterior.getComunidad(),anterior.getMunicipio(),anterior.getDepartamento(),
                anterior.getTelefono(),String.valueOf(anterior.getIntegrantes()),anterior.getCondiciones(),anterior.getFechaRegistro().toString(),anterior.getEstado(),anterior.getObservaciones()};
        for(int i=0;i<campos.length;i++){campos[i]=new JTextField(valores[i]==null?"":valores[i],23);p.add(new JLabel(etiquetas[i]+" :"));p.add(campos[i]);}
        JScrollPane panel=new JScrollPane(p);panel.setPreferredSize(new Dimension(650,480));
        if(JOptionPane.showConfirmDialog(this,panel,id==null?"Registrar nueva familia":"Modificar familia",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        Familia f=new Familia(id==null?0:id,campos[0].getText().trim(),campos[1].getText().trim(),campos[2].getText().trim(),
            campos[3].getText().trim(),campos[4].getText().trim(),campos[5].getText().trim(),campos[6].getText().trim(),
            campos[7].getText().trim(),Integer.parseInt(campos[8].getText().trim()),campos[9].getText().trim(),
            LocalDate.parse(campos[10].getText().trim()),campos[11].getText().trim(),campos[12].getText().trim());
        if(id==null)dao.crear(f);else dao.actualizar(f);
        cargar();JOptionPane.showMessageDialog(this,"Familia guardada.");
    }
}
