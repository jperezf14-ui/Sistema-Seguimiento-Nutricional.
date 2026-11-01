package umg.nutricion;

import java.time.LocalDate;

/** Modelo orientado a objetos: encapsulamiento, constructor y validación. */
public final class Familia {
    private int id;
    private String codigo, responsable, identificacion, direccion, comunidad, municipio, departamento, telefono;
    private int integrantes;
    private String condiciones, estado, observaciones;
    private LocalDate fechaRegistro;

    public Familia(int id,String codigo,String responsable,String identificacion,String direccion,String comunidad,
                   String municipio,String departamento,String telefono,int integrantes,String condiciones,
                   LocalDate fechaRegistro,String estado,String observaciones){
        this.id=id;this.codigo=codigo;this.responsable=responsable;this.identificacion=identificacion;
        this.direccion=direccion;this.comunidad=comunidad;this.municipio=municipio;
        this.departamento=departamento;this.telefono=telefono;this.integrantes=integrantes;
        this.condiciones=condiciones;this.fechaRegistro=fechaRegistro;this.estado=estado;this.observaciones=observaciones;
        validar();
    }
    public void validar(){
        if(codigo==null||codigo.isBlank()||responsable==null||responsable.isBlank()||identificacion==null||identificacion.isBlank()
                ||direccion==null||direccion.isBlank()||comunidad==null||comunidad.isBlank()||municipio==null||municipio.isBlank()
                ||departamento==null||departamento.isBlank())throw new IllegalArgumentException("Completa todos los campos obligatorios de la familia.");
        if(integrantes<1)throw new IllegalArgumentException("La familia debe tener al menos un integrante.");
        if(fechaRegistro==null||fechaRegistro.isAfter(LocalDate.now()))throw new IllegalArgumentException("Fecha de registro inválida.");
    }
    public int getId(){return id;}
    public String getCodigo(){return codigo;}
    public String getResponsable(){return responsable;}
    public String getIdentificacion(){return identificacion;}
    public String getDireccion(){return direccion;}
    public String getComunidad(){return comunidad;}
    public String getMunicipio(){return municipio;}
    public String getDepartamento(){return departamento;}
    public String getTelefono(){return telefono;}
    public int getIntegrantes(){return integrantes;}
    public String getCondiciones(){return condiciones;}
    public LocalDate getFechaRegistro(){return fechaRegistro;}
    public String getEstado(){return estado;}
    public String getObservaciones(){return observaciones;}
}
