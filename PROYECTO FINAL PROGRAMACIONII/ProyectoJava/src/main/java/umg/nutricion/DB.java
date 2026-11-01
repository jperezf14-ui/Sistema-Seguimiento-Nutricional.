package umg.nutricion;

import java.sql.*;
import java.util.*;
import javax.swing.*;

public final class DB {
    private DB() {}
    public static Connection conn;
    public static void conectar() throws Exception {
        JTextField host = new JTextField("localhost");
        JTextField puerto = new JTextField("3306");
        JTextField usuario = new JTextField("root");
        JPasswordField pass = new JPasswordField();
        Object[] formulario = {"Servidor MySQL:",host,"Puerto:",puerto,"Usuario MySQL:",usuario,"Contraseña MySQL:",pass};
        if (JOptionPane.showConfirmDialog(null,formulario,"Conectar con MySQL",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)
            throw new IllegalStateException("Conexión cancelada");
        Class.forName("com.mysql.cj.jdbc.Driver");
        String url = "jdbc:mysql://"+host.getText().trim()+":"+puerto.getText().trim()+"/seguimiento_nutricional?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Guatemala";
        conn = DriverManager.getConnection(url,usuario.getText().trim(),new String(pass.getPassword()));
    }
    public static int cuenta(String tabla) throws SQLException {
        try(Statement st=conn.createStatement(); ResultSet r=st.executeQuery("SELECT COUNT(*) FROM "+tabla)) {r.next();return r.getInt(1);}
    }
    public static int idRol(String nombre) throws SQLException {
        try(PreparedStatement p=conn.prepareStatement("SELECT id_rol FROM rol WHERE nombre=?")){
            p.setString(1,nombre); try(ResultSet r=p.executeQuery()){if(r.next())return r.getInt(1);}
        }
        throw new SQLException("Rol inexistente: "+nombre+". Ejecuta BaseDatos.sql.");
    }
    public static void crearAdminInicial() throws Exception {
        if(cuenta("usuario")!=0)return;
        JTextField nombre=new JTextField("Administrador"),usuario=new JTextField("admin"),correo=new JTextField("admin@institucion.local");
        JPasswordField clave=new JPasswordField();
        Object[] o={"Primer inicio: crear administrador", "Nombre completo",nombre,"Usuario de acceso",usuario,"Correo",correo,"Contraseña (8 caracteres o más)",clave};
        int ok=JOptionPane.showConfirmDialog(null,o,"Configuración inicial",JOptionPane.OK_CANCEL_OPTION);
        if(ok!=JOptionPane.OK_OPTION) throw new IllegalStateException("No se creó el administrador");
        char[] password=clave.getPassword();
        if(password.length<8 || nombre.getText().isBlank()||usuario.getText().isBlank())throw new IllegalArgumentException("Nombre, usuario y contraseña de 8+ caracteres obligatorios");
        String hash=Passwords.hash(password);
        try(PreparedStatement p=conn.prepareStatement("INSERT INTO usuario(id_rol,nombre_completo,nombre_usuario,correo,clave_hash) VALUES(?,?,?,?,?)")) {
            p.setInt(1,idRol("Administrador"));p.setString(2,nombre.getText().trim());p.setString(3,usuario.getText().trim());p.setString(4,correo.getText().trim());p.setString(5,hash);p.executeUpdate();
        }
        JOptionPane.showMessageDialog(null,"Administrador creado. Guarda tu usuario y contraseña.");
    }
    public static Sesion entrar() throws Exception {
        for(int i=0;i<4;i++){
            JTextField user=new JTextField("admin");JPasswordField clave=new JPasswordField();
            if(JOptionPane.showConfirmDialog(null,new Object[]{"Usuario",user,"Contraseña",clave},"Iniciar sesión",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return null;
            try(PreparedStatement p=conn.prepareStatement("SELECT u.id_usuario,u.nombre_completo,r.nombre,u.clave_hash FROM usuario u JOIN rol r ON u.id_rol=r.id_rol WHERE u.nombre_usuario=? AND u.estado='Activo' AND r.activo=1")){
                p.setString(1,user.getText().trim());try(ResultSet rs=p.executeQuery()){
                    if(rs.next() && Passwords.verificar(clave.getPassword(),rs.getString(4)))return new Sesion(rs.getInt(1),rs.getString(2),rs.getString(3));
                }
            }
            JOptionPane.showMessageDialog(null,"Credenciales incorrectas.","Acceso",JOptionPane.WARNING_MESSAGE);
        }
        return null;
    }
    public static Object scalar(String sql,Object... args) throws SQLException {
        try(PreparedStatement p=conn.prepareStatement(sql)){
            bind(p,args);try(ResultSet r=p.executeQuery()){return r.next()?r.getObject(1):null;}
        }
    }
    public static void bind(PreparedStatement p,Object... args) throws SQLException {
        for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);
    }
    public record Sesion(int id,String nombre,String rol) {
        public boolean admin(){return rol.equals("Administrador");}
        public boolean salud(){return rol.equals("Profesional de salud o nutrición");}
        public boolean promotor(){return rol.equals("Promotor comunitario");}
        public boolean coordinador(){return rol.equals("Coordinador");}
    }
}
