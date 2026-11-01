package umg.nutricion;

import javax.swing.*;
import java.awt.*;

public final class MainFrame extends JFrame {
    private final DB.Sesion sesion;
    private final JTabbedPane tabs=new JTabbedPane();
    public MainFrame(DB.Sesion sesion) throws Exception {
        super("UMG | Seguimiento nutricional y ayudas");this.sesion=sesion;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);setSize(1240,750);setMinimumSize(new Dimension(930,580));
        JPanel cab=new JPanel(new BorderLayout(8,8));cab.setBorder(BorderFactory.createEmptyBorder(9,15,9,15));
        JLabel title=new JLabel("Sistema de seguimiento nutricional y control de ayudas");title.setFont(title.getFont().deriveFont(Font.BOLD,18f));cab.add(title,BorderLayout.WEST);
        cab.add(new JLabel("Usuario: "+sesion.nombre()+" | "+sesion.rol()),BorderLayout.EAST);
        add(cab,BorderLayout.NORTH);
        add(tabs,BorderLayout.CENTER);
        if(sesion.admin()){
            addCrud("Institución","institucion",true);
            addCrud("Usuarios","usuario",true);
            addCrud("Roles","rol",false);
        }
        if(sesion.admin()||sesion.promotor()||sesion.salud()||sesion.coordinador()){
            tabs.addTab("Familias",new FamiliasPanel(sesion.admin()||sesion.promotor()));
            addCrud("Niños","nino",sesion.admin()||sesion.promotor());
        }
        if(sesion.salud()||sesion.admin()||sesion.coordinador()){
            addCrud("Controles","control_nutricional",sesion.salud());
            addCrud("Evaluaciones","evaluacion_seguimiento",sesion.salud());
            addCrud("Referencias","referencia",sesion.salud());
        }
        if(sesion.salud()||sesion.promotor()||sesion.admin()||sesion.coordinador()){
            addCrud("Citas","cita",sesion.salud()||sesion.promotor());
            addCrud("Visitas","visita_comunitaria",sesion.promotor());
            addCrud("Productos","producto_ayuda",sesion.admin());
            addCrud("Lotes","lote_ayuda",sesion.admin());
            addCrud("Entregas","entrega_ayuda",sesion.promotor()||sesion.admin());
            addCrud("Detalle entregas","detalle_entrega",sesion.promotor()||sesion.admin());
            addCrud("Movimientos","movimiento_inventario",false);
        }
        tabs.addTab("Reportes",new ReportesPanel());
        JPanel acciones=new JPanel(new FlowLayout(FlowLayout.LEFT,10,7));
        if(sesion.admin())acciones.add(boton("+ Entrada de inventario",()->Inventario.entrada(sesion)));
        if(sesion.admin()||sesion.promotor())acciones.add(boton("Confirmar entrega",()->Inventario.confirmar(sesion)));
        acciones.add(boton("Ficha nutricional PDF",Documentos::ficha));
        acciones.add(boton("Constancia de entrega PDF",Documentos::constancia));
        JButton ayuda=new JButton("Guía rápida");ayuda.addActionListener(e->JOptionPane.showMessageDialog(this,
            "ORDEN RECOMENDADO:\n1. Registrar la institución y usuarios.\n2. Crear familias y niños.\n3. Registrar controles, citas o visitas.\n4. Crear producto y lote; ingresar existencia con '+ Entrada'.\n5. Crear una entrega en Borrador y sus detalles.\n6. Confirmar entrega (descuenta el inventario).\n7. Generar PDF y consultar los reportes.\n\nCampos marcados * son obligatorios. Las FK se eligen desde listas.","Ayuda",JOptionPane.INFORMATION_MESSAGE));
        acciones.add(ayuda);add(acciones,BorderLayout.SOUTH);
    }
    private void addCrud(String titulo,String tabla,boolean editable) throws Exception {
        tabs.addTab(titulo,new CrudPanel(tabla,sesion,editable));
    }
    interface Accion {void hacer() throws Exception;}
    private JButton boton(String text,Accion accion){JButton b=new JButton(text);b.addActionListener(e->{try{accion.hacer();}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);}});return b;}
}
