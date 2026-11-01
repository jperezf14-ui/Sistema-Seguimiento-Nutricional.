package umg.nutricion;

import javax.swing.*;
import java.awt.*;

public final class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(()->{
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                DB.conectar();
                DB.crearAdminInicial();
                DB.Sesion sesion=DB.entrar();
                if(sesion==null) {System.exit(0);return;}
                MainFrame frame=new MainFrame(sesion);
                frame.setLocationRelativeTo(null);frame.setVisible(true);
            } catch(Exception e){
                JOptionPane.showMessageDialog(null,"No se pudo iniciar el sistema:\n"+e.getMessage()+"\n\nVerifica MySQL y ejecuta BaseDatos.sql primero.","Error",JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
