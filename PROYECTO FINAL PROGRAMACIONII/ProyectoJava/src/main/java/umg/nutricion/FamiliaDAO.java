package umg.nutricion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;

/** Capa de acceso a datos JDBC: las ventanas no ejecutan SQL de familias. */
public final class FamiliaDAO {
    private final Connection conexion;
    public FamiliaDAO(Connection conexion){this.conexion=conexion;}
    private static final String CAMPOS="codigo,nombre_responsable,numero_identificacion,direccion,comunidad,municipio,departamento,telefono,cantidad_integrantes,condiciones_hogar,fecha_registro,estado,observaciones";
    public void crear(Familia f)throws SQLException {
        String sql="INSERT INTO familia("+CAMPOS+") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try(PreparedStatement p=conexion.prepareStatement(sql)){asignar(p,f);p.executeUpdate();}
    }
    public void actualizar(Familia f)throws SQLException {
        String sql="UPDATE familia SET codigo=?,nombre_responsable=?,numero_identificacion=?,direccion=?,comunidad=?,municipio=?,departamento=?,telefono=?,cantidad_integrantes=?,condiciones_hogar=?,fecha_registro=?,estado=?,observaciones=? WHERE id_familia=?";
        try(PreparedStatement p=conexion.prepareStatement(sql)){asignar(p,f);p.setInt(14,f.getId());p.executeUpdate();}
    }
    public void eliminar(int id)throws SQLException {
        try(PreparedStatement p=conexion.prepareStatement("DELETE FROM familia WHERE id_familia=?")){p.setInt(1,id);p.executeUpdate();}
    }
    public Familia buscar(int id)throws SQLException {
        try(PreparedStatement p=conexion.prepareStatement("SELECT * FROM familia WHERE id_familia=?")){
            p.setInt(1,id);try(ResultSet r=p.executeQuery()){return r.next()?convertir(r):null;}
        }
    }
    public List<Familia> listar()throws SQLException {
        List<Familia> x=new ArrayList<>();
        try(Statement p=conexion.createStatement();ResultSet r=p.executeQuery("SELECT * FROM familia ORDER BY id_familia DESC LIMIT 500")){
            while(r.next())x.add(convertir(r));
        }
        return x;
    }
    private static void asignar(PreparedStatement p,Familia f)throws SQLException {
        p.setString(1,f.getCodigo());p.setString(2,f.getResponsable());p.setString(3,f.getIdentificacion());
        p.setString(4,f.getDireccion());p.setString(5,f.getComunidad());p.setString(6,f.getMunicipio());
        p.setString(7,f.getDepartamento());p.setString(8,f.getTelefono());p.setInt(9,f.getIntegrantes());
        p.setString(10,f.getCondiciones());p.setDate(11,java.sql.Date.valueOf(f.getFechaRegistro()));
        p.setString(12,f.getEstado());p.setString(13,f.getObservaciones());
    }
    private static Familia convertir(ResultSet r)throws SQLException {
        return new Familia(r.getInt("id_familia"),r.getString("codigo"),r.getString("nombre_responsable"),
            r.getString("numero_identificacion"),r.getString("direccion"),r.getString("comunidad"),
            r.getString("municipio"),r.getString("departamento"),r.getString("telefono"),
            r.getInt("cantidad_integrantes"),r.getString("condiciones_hogar"),
            r.getDate("fecha_registro").toLocalDate(),r.getString("estado"),r.getString("observaciones"));
    }
}
