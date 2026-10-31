# Sistema de seguimiento nutricional y control de ayudas

Proyecto académico de Programación II — Universidad Mariano Gálvez de Guatemala.

Aplicación de escritorio desarrollada con Java 21, Swing, Maven y MySQL 8 para registrar familias, beneficiarios, controles nutricionales, citas, entregas de ayuda e inventario, y generar reportes.

## Estructura
- `ProyectoJava/src/main/java/umg/nutricion/`: código fuente de la aplicación.
- `ProyectoJava/pom.xml`: dependencias y configuración de Maven.
- `BaseDatos.sql`: estructura de la base de datos y roles iniciales.

## Ejecución
1. Tener instalados Java 21, Maven y MySQL 8.
2. Ejecutar `BaseDatos.sql` en MySQL.
3. Abrir `ProyectoJava` como proyecto Maven en NetBeans 21.
4. Ejecutar la clase `umg.nutricion.App` e introducir la conexión MySQL solicitada.

**Privacidad:** no subir datos reales de familias o pacientes, contraseñas ni archivos de configuración local. Los reportes PDF generados con datos de prueba y el informe con datos personales no se incluyen en este repositorio público.
