package umg.nutricion;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/** Panel de 13 reportes con filtros en SQL (parametrizados) y exportación PDF. */
public final class ReportesPanel extends JPanel {

    private record Informe(String titulo) {
        @Override public String toString() { return titulo; }
    }

    private record Opcion(int id, String texto) {
        @Override public String toString() { return texto; }
    }

    /**
     * inicio termina en una condición WHERE. fin contiene GROUP BY/ORDER BY.
     * Los campos opcionales son expresiones SQL FIJAS, nunca texto del usuario.
     */
    private record Consulta(
            String inicio, String fin,
            String campoNino, String campoFamilia, String campoComunidad,
            String campoResponsable, String campoEstado, String campoFecha,
            String tipoEstado, String descripcionFecha) {
    }

    private static final Informe[] INFORMES = {
        new Informe("01. Niños por comunidad"),
        new Informe("02. Niños por estado de seguimiento"),
        new Informe("03. Historial de controles por niño"),
        new Informe("04. Citas por estado"),
        new Informe("05. Niños que requieren control"),
        new Informe("06. Evolución de peso y talla"),
        new Informe("07. Visitas comunitarias"),
        new Informe("08. Referencias emitidas y pendientes"),
        new Informe("09. Ayudas entregadas por periodo"),
        new Informe("10. Historial de ayudas por niño o familia"),
        new Informe("11. Inventario de ayudas"),
        new Informe("12. Productos próximos a vencer (30 días)"),
        new Informe("13. Atenciones por responsable")
    };

    private final JComboBox<Informe> informe = new JComboBox<>(INFORMES);
    private final JComboBox<Opcion> nino = new JComboBox<>();
    private final JComboBox<Opcion> familia = new JComboBox<>();
    private final JComboBox<Opcion> responsable = new JComboBox<>();
    private final JComboBox<String> comunidad = new JComboBox<>();
    private final JComboBox<String> estado = new JComboBox<>();
    private final JTextField desde = new JTextField(9);
    private final JTextField hasta = new JTextField(9);
    private final JTextField buscar = new JTextField(13);
    private final JLabel avisoFecha = new JLabel("Fechas:");
    private final JLabel nota = new JLabel("Selecciona un reporte y sus filtros.");
    private final JTable tabla = new JTable();
    private final DefaultTableModel modelo = new DefaultTableModel() {
        @Override public boolean isCellEditable(int fila, int columna) { return false; }
    };

    public ReportesPanel() {
        super(new BorderLayout(6, 6));
        JPanel cabecera = new JPanel();
        cabecera.setLayout(new BoxLayout(cabecera, BoxLayout.Y_AXIS));

        JPanel fila1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        fila1.add(new JLabel("Reporte:"));
        fila1.add(informe);
        JButton consultar = new JButton("Consultar");
        consultar.addActionListener(e -> cargar());
        fila1.add(consultar);
        fila1.add(new JLabel("Filtrar texto (tabla):"));
        fila1.add(buscar);
        JButton exportar = new JButton("Exportar resultado a PDF");
        exportar.addActionListener(e -> exportar());
        fila1.add(exportar);
        cabecera.add(fila1);

        JPanel fila2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        nino.setPrototypeDisplayValue(new Opcion(0, "00000 - Nombre completo largo"));
        familia.setPrototypeDisplayValue(new Opcion(0, "00000 - Responsable familiar"));
        responsable.setPrototypeDisplayValue(new Opcion(0, "00000 - Profesional responsable"));
        comunidad.setPrototypeDisplayValue("Comunidad con nombre largo");
        estado.setPrototypeDisplayValue("En seguimiento");
        fila2.add(new JLabel("Niño:")); fila2.add(nino);
        fila2.add(new JLabel("Familia:")); fila2.add(familia);
        fila2.add(new JLabel("Comunidad:")); fila2.add(comunidad);
        cabecera.add(fila2);

        JPanel fila3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        fila3.add(new JLabel("Responsable:")); fila3.add(responsable);
        fila3.add(new JLabel("Estado:")); fila3.add(estado);
        fila3.add(avisoFecha);
        fila3.add(new JLabel("Desde:")); fila3.add(desde);
        fila3.add(new JLabel("Hasta:")); fila3.add(hasta);
        JButton limpiar = new JButton("Limpiar filtros");
        limpiar.addActionListener(e -> limpiarFiltros());
        fila3.add(limpiar);
        JButton recargar = new JButton("Recargar listas");
        recargar.addActionListener(e -> { cargarCatalogos(); cargar(); });
        fila3.add(recargar);
        cabecera.add(fila3);

        add(cabecera, BorderLayout.NORTH);
        tabla.setModel(modelo);
        tabla.setAutoCreateRowSorter(true);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabla.setRowHeight(24);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(nota, BorderLayout.SOUTH);
        desde.setToolTipText("Formato AAAA-MM-DD; opcional");
        hasta.setToolTipText("Formato AAAA-MM-DD; opcional");
        buscar.setToolTipText("Busca dentro de las filas ya consultadas (filtro local adicional)");

        buscar.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filtrarTexto(); }
            @Override public void removeUpdate(DocumentEvent e) { filtrarTexto(); }
            @Override public void changedUpdate(DocumentEvent e) { filtrarTexto(); }
        });

        cargarCatalogos();
        informe.addActionListener(e -> {
            prepararFiltros();
            cargar();
        });
        prepararFiltros();
        cargar();
    }

    private void cargarCatalogos() {
        if (DB.conn == null) {
            JOptionPane.showMessageDialog(this, "No existe una conexión MySQL activa.");
            return;
        }
        try {
            llenarOpciones(nino,
                    "SELECT id_nino, CONCAT(codigo, ' - ', nombres, ' ', apellidos) AS nombre "
                    + "FROM nino ORDER BY nombres, apellidos");
            llenarOpciones(familia,
                    "SELECT id_familia, CONCAT(codigo, ' - ', nombre_responsable) AS nombre "
                    + "FROM familia ORDER BY nombre_responsable");
            llenarOpciones(responsable,
                    "SELECT id_usuario, nombre_completo AS nombre FROM usuario ORDER BY nombre_completo");

            String seleccionado = (String) comunidad.getSelectedItem();
            comunidad.removeAllItems();
            comunidad.addItem("Todas");
            try (Statement st = DB.conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT DISTINCT comunidad FROM nino "
                         + "WHERE comunidad IS NOT NULL AND comunidad <> '' ORDER BY comunidad")) {
                while (rs.next()) comunidad.addItem(rs.getString(1));
            }
            if (seleccionado != null) comunidad.setSelectedItem(seleccionado);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error al cargar listas: " + e.getMessage());
        }
    }

    private void llenarOpciones(JComboBox<Opcion> combo, String sql) throws SQLException {
        int idAnterior = idSeleccionado(combo);
        combo.removeAllItems();
        combo.addItem(new Opcion(0, "Todos"));
        try (Statement st = DB.conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) combo.addItem(new Opcion(rs.getInt(1), rs.getString(2)));
        }
        for (int k = 0; k < combo.getItemCount(); k++) {
            if (combo.getItemAt(k).id() == idAnterior) {
                combo.setSelectedIndex(k);
                return;
            }
        }
        combo.setSelectedIndex(0);
    }

    private int idSeleccionado(JComboBox<Opcion> combo) {
        Opcion opcion = (Opcion) combo.getSelectedItem();
        return opcion == null ? 0 : opcion.id();
    }

    private Consulta consultaActual() {
        return switch (informe.getSelectedIndex()) {
            case 0 -> new Consulta(
                "SELECT n.comunidad, COUNT(*) AS total FROM nino n WHERE 1=1",
                " GROUP BY n.comunidad ORDER BY n.comunidad",
                "n.id_nino", "n.id_familia", "n.comunidad", null,
                "n.estado_seguimiento", "n.fecha_ingreso", "NINO", "Ingreso");
            case 1 -> new Consulta(
                "SELECT n.estado_seguimiento, COUNT(*) AS total FROM nino n WHERE 1=1",
                " GROUP BY n.estado_seguimiento ORDER BY total DESC",
                "n.id_nino", "n.id_familia", "n.comunidad", null,
                "n.estado_seguimiento", "n.fecha_ingreso", "NINO", "Ingreso");
            case 2 -> new Consulta(
                "SELECT n.codigo, CONCAT(n.nombres, ' ', n.apellidos) AS nino, "
                    + "c.numero_control, c.fecha_control, c.peso_kg, c.talla_cm, c.clasificacion_nutricional "
                    + "FROM control_nutricional c JOIN nino n ON n.id_nino=c.id_nino WHERE 1=1",
                " ORDER BY n.codigo, c.fecha_control",
                "n.id_nino", "n.id_familia", "n.comunidad", "c.id_profesional",
                "n.estado_seguimiento", "c.fecha_control", "NINO", "Control");
            case 3 -> new Consulta(
                "SELECT c.estado, c.fecha_cita, COUNT(*) AS total FROM cita c "
                    + "JOIN nino n ON n.id_nino=c.id_nino WHERE 1=1",
                " GROUP BY c.estado, c.fecha_cita ORDER BY c.fecha_cita DESC",
                "n.id_nino", "n.id_familia", "n.comunidad", "c.id_profesional",
                "c.estado", "c.fecha_cita", "CITA", "Cita");
            case 4 -> new Consulta(
                "SELECT n.codigo, CONCAT(n.nombres, ' ', n.apellidos) AS nino, "
                    + "c.fecha_control AS ultimo_control, c.proximo_control "
                    + "FROM nino n JOIN control_nutricional c ON c.id_nino=n.id_nino "
                    + "WHERE 1=1 AND c.proximo_control IS NOT NULL "
                    + "AND c.proximo_control <= CURDATE() "
                    + "AND NOT EXISTS (SELECT 1 FROM control_nutricional otro "
                    + "WHERE otro.id_nino=c.id_nino AND "
                    + "(otro.fecha_control>c.fecha_control OR "
                    + "(otro.fecha_control=c.fecha_control AND otro.id_control>c.id_control)))",
                " ORDER BY c.proximo_control",
                "n.id_nino", "n.id_familia", "n.comunidad", "c.id_profesional",
                "n.estado_seguimiento", "c.proximo_control", "NINO", "Próximo control");
            case 5 -> new Consulta(
                "SELECT n.codigo, CONCAT(n.nombres, ' ', n.apellidos) AS nino, "
                    + "c.fecha_control, c.peso_kg, c.talla_cm "
                    + "FROM control_nutricional c JOIN nino n ON n.id_nino=c.id_nino WHERE 1=1",
                " ORDER BY n.codigo, c.fecha_control",
                "n.id_nino", "n.id_familia", "n.comunidad", "c.id_profesional",
                "n.estado_seguimiento", "c.fecha_control", "NINO", "Control");
            case 6 -> new Consulta(
                "SELECT v.id_visita, v.fecha_hora, n.codigo AS nino, "
                    + "f.nombre_responsable AS familia, u.nombre_completo AS promotor, v.motivo "
                    + "FROM visita_comunitaria v "
                    + "JOIN nino n ON n.id_nino=v.id_nino "
                    + "JOIN familia f ON f.id_familia=v.id_familia "
                    + "JOIN usuario u ON u.id_usuario=v.id_promotor WHERE 1=1",
                " ORDER BY v.fecha_hora DESC",
                "v.id_nino", "v.id_familia", "n.comunidad", "v.id_promotor",
                null, "v.fecha_hora", "NINGUNO", "Visita");
            case 7 -> new Consulta(
                "SELECT r.id_referencia, r.fecha_referencia, n.codigo AS nino, "
                    + "r.institucion_destino, r.tipo_servicio, r.estado "
                    + "FROM referencia r JOIN nino n ON n.id_nino=r.id_nino "
                    + "WHERE 1=1 AND r.estado IN ('Emitida','Pendiente')",
                " ORDER BY r.fecha_referencia DESC",
                "r.id_nino", "n.id_familia", "n.comunidad", "r.id_profesional",
                "r.estado", "r.fecha_referencia", "REFERENCIA", "Referencia");
            case 8 -> new Consulta(
                "SELECT DATE(e.fecha_entrega) AS fecha, COUNT(DISTINCT e.id_entrega) AS entregas, "
                    + "SUM(d.cantidad) AS cantidad "
                    + "FROM entrega_ayuda e JOIN detalle_entrega d ON d.id_entrega=e.id_entrega "
                    + "JOIN nino n ON n.id_nino=e.id_nino "
                    + "WHERE 1=1 AND e.estado='Confirmada'",
                " GROUP BY DATE(e.fecha_entrega) ORDER BY fecha DESC",
                "e.id_nino", "e.id_familia", "n.comunidad", "e.id_usuario_entrega",
                null, "e.fecha_entrega", "NINGUNO", "Entrega");
            case 9 -> new Consulta(
                "SELECT n.codigo AS nino, f.nombre_responsable AS familia, e.numero_entrega, "
                    + "e.fecha_entrega, p.nombre AS producto, d.cantidad "
                    + "FROM entrega_ayuda e JOIN nino n ON n.id_nino=e.id_nino "
                    + "JOIN familia f ON f.id_familia=e.id_familia "
                    + "JOIN detalle_entrega d ON d.id_entrega=e.id_entrega "
                    + "JOIN lote_ayuda l ON l.id_lote=d.id_lote "
                    + "JOIN producto_ayuda p ON p.id_producto=l.id_producto "
                    + "WHERE 1=1 AND e.estado='Confirmada'",
                " ORDER BY e.fecha_entrega DESC",
                "e.id_nino", "e.id_familia", "n.comunidad", "e.id_usuario_entrega",
                null, "e.fecha_entrega", "NINGUNO", "Entrega");
            case 10 -> new Consulta(
                "SELECT p.codigo, p.nombre, p.tipo, p.unidad_medida, "
                    + "COALESCE(SUM(l.existencia_actual), 0) AS existencia "
                    + "FROM producto_ayuda p LEFT JOIN lote_ayuda l ON l.id_producto=p.id_producto "
                    + "WHERE 1=1",
                " GROUP BY p.id_producto, p.codigo, p.nombre, p.tipo, p.unidad_medida ORDER BY p.nombre",
                null, null, null, null, "p.estado", null, "PRODUCTO", "");
            case 11 -> new Consulta(
                "SELECT p.nombre, l.codigo_lote, l.fecha_vencimiento, l.existencia_actual "
                    + "FROM lote_ayuda l JOIN producto_ayuda p ON p.id_producto=l.id_producto "
                    + "WHERE 1=1 AND l.fecha_vencimiento BETWEEN CURDATE() "
                    + "AND DATE_ADD(CURDATE(), INTERVAL 30 DAY) AND l.existencia_actual>0",
                " ORDER BY l.fecha_vencimiento",
                null, null, null, null, "l.estado", "l.fecha_vencimiento",
                "LOTE", "Vencimiento (dentro de 30 días)");
            case 12 -> new Consulta(
                "SELECT u.nombre_completo AS responsable, "
                    + "COALESCE(SUM(CASE WHEN z.tipo='CONTROL' THEN 1 ELSE 0 END),0) AS controles, "
                    + "COALESCE(SUM(CASE WHEN z.tipo='VISITA' THEN 1 ELSE 0 END),0) AS visitas, "
                    + "COALESCE(SUM(CASE WHEN z.tipo='ENTREGA' THEN 1 ELSE 0 END),0) AS entregas "
                    + "FROM usuario u LEFT JOIN ("
                    + "SELECT c.id_profesional AS usuario_id, n.id_nino AS nino_id, "
                    + "n.id_familia AS familia_id, n.comunidad AS comunidad, "
                    + "n.estado_seguimiento AS estado_nino, c.fecha_control AS fecha, 'CONTROL' AS tipo "
                    + "FROM control_nutricional c JOIN nino n ON n.id_nino=c.id_nino "
                    + "UNION ALL "
                    + "SELECT v.id_promotor, n.id_nino, v.id_familia, n.comunidad, "
                    + "n.estado_seguimiento, DATE(v.fecha_hora), 'VISITA' "
                    + "FROM visita_comunitaria v JOIN nino n ON n.id_nino=v.id_nino "
                    + "UNION ALL "
                    + "SELECT e.id_usuario_entrega, n.id_nino, e.id_familia, n.comunidad, "
                    + "n.estado_seguimiento, DATE(e.fecha_entrega), 'ENTREGA' "
                    + "FROM entrega_ayuda e JOIN nino n ON n.id_nino=e.id_nino "
                    + "WHERE e.estado='Confirmada') z ON z.usuario_id=u.id_usuario WHERE 1=1",
                " GROUP BY u.id_usuario, u.nombre_completo ORDER BY u.nombre_completo",
                "z.nino_id", "z.familia_id", "z.comunidad", "u.id_usuario",
                "z.estado_nino", "z.fecha", "NINO", "Atención");
            default -> throw new IllegalStateException("Reporte no válido");
        };
    }

    private void prepararFiltros() {
        Consulta c = consultaActual();
        nino.setEnabled(c.campoNino() != null);
        familia.setEnabled(c.campoFamilia() != null);
        comunidad.setEnabled(c.campoComunidad() != null);
        responsable.setEnabled(c.campoResponsable() != null);
        desde.setEnabled(c.campoFecha() != null);
        hasta.setEnabled(c.campoFecha() != null);
        avisoFecha.setText(c.campoFecha() == null ? "Sin filtro de fechas" :
                "Fecha de " + c.descripcionFecha() + " (AAAA-MM-DD):");
        estado.removeAllItems();
        estado.addItem("Todos");
        switch (c.tipoEstado()) {
            case "NINO" -> {
                for (String s : new String[]{"En evaluación", "En seguimiento", "Estable",
                    "Referido", "Finalizado", "Inactivo"}) estado.addItem(s);
            }
            case "CITA" -> {
                for (String s : new String[]{"Programada", "Atendida", "Cancelada",
                    "Reprogramada", "No asistió"}) estado.addItem(s);
            }
            case "REFERENCIA" -> {
                estado.addItem("Emitida"); estado.addItem("Pendiente");
            }
            case "PRODUCTO" -> {
                estado.addItem("Activo"); estado.addItem("Inactivo");
            }
            case "LOTE" -> {
                estado.addItem("Activo"); estado.addItem("Bloqueado");
            }
            default -> { }
        }
        estado.setEnabled(c.campoEstado() != null);
    }

    private void limpiarFiltros() {
        if (nino.getItemCount() > 0) nino.setSelectedIndex(0);
        if (familia.getItemCount() > 0) familia.setSelectedIndex(0);
        if (comunidad.getItemCount() > 0) comunidad.setSelectedIndex(0);
        if (responsable.getItemCount() > 0) responsable.setSelectedIndex(0);
        if (estado.getItemCount() > 0) estado.setSelectedIndex(0);
        desde.setText("");
        hasta.setText("");
        buscar.setText("");
        cargar();
    }

    private LocalDate fecha(String texto, String nombre) {
        if (texto.isBlank()) return null;
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha " + nombre + " inválida. Usa AAAA-MM-DD.");
        }
    }

    private void cargar() {
        if (DB.conn == null) {
            JOptionPane.showMessageDialog(this, "No hay conexión a MySQL.");
            return;
        }
        try {
            Consulta c = consultaActual();
            LocalDate inicio = c.campoFecha() == null ? null : fecha(desde.getText(), "Desde");
            LocalDate fin = c.campoFecha() == null ? null : fecha(hasta.getText(), "Hasta");
            if (inicio != null && fin != null && inicio.isAfter(fin)) {
                throw new IllegalArgumentException("La fecha Desde no puede ser posterior a Hasta.");
            }
            StringBuilder sql = new StringBuilder(c.inicio());
            List<Object> parametros = new ArrayList<>();
            agregarId(sql, parametros, c.campoNino(), idSeleccionado(nino));
            agregarId(sql, parametros, c.campoFamilia(), idSeleccionado(familia));
            agregarId(sql, parametros, c.campoResponsable(), idSeleccionado(responsable));
            if (c.campoComunidad() != null && comunidad.getSelectedIndex() > 0) {
                sql.append(" AND ").append(c.campoComunidad()).append(" = ?");
                parametros.add(comunidad.getSelectedItem());
            }
            if (c.campoEstado() != null && estado.getSelectedIndex() > 0) {
                sql.append(" AND ").append(c.campoEstado()).append(" = ?");
                parametros.add(estado.getSelectedItem());
            }
            if (c.campoFecha() != null && inicio != null) {
                sql.append(" AND ").append(c.campoFecha()).append(" >= ?");
                parametros.add(Date.valueOf(inicio));
            }
            if (c.campoFecha() != null && fin != null) {
                // < día siguiente incluye también horas de la fecha final.
                sql.append(" AND ").append(c.campoFecha())
                    .append(" < DATE_ADD(?, INTERVAL 1 DAY)");
                parametros.add(Date.valueOf(fin));
            }
            sql.append(c.fin());
            try (PreparedStatement ps = DB.conn.prepareStatement(sql.toString())) {
                for (int k = 0; k < parametros.size(); k++) {
                    ps.setObject(k + 1, parametros.get(k));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    String[] columnas = new String[md.getColumnCount()];
                    for (int k = 1; k <= columnas.length; k++) {
                        columnas[k - 1] = md.getColumnLabel(k);
                    }
                    modelo.setColumnIdentifiers(columnas);
                    modelo.setRowCount(0);
                    while (rs.next()) {
                        Object[] datos = new Object[columnas.length];
                        for (int k = 0; k < datos.length; k++) datos[k] = rs.getObject(k + 1);
                        modelo.addRow(datos);
                    }
                }
            }
            for (int k = 0; k < tabla.getColumnCount(); k++) {
                tabla.getColumnModel().getColumn(k).setPreferredWidth(175);
            }
            filtrarTexto();
            nota.setText(modelo.getRowCount() + " fila(s) consultadas en MySQL. "
                    + "Filtros avanzados aplicados antes del reporte; el texto busca entre las filas visibles.");
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Revisar filtros",
                    JOptionPane.WARNING_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error en consulta: " + e.getMessage(),
                    "MySQL", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void agregarId(StringBuilder sql, List<Object> params, String columna, int id) {
        if (columna != null && id > 0) {
            sql.append(" AND ").append(columna).append(" = ?");
            params.add(id);
        }
    }

    private void filtrarTexto() {
        if (tabla.getRowSorter() instanceof TableRowSorter<?> sorter) {
            String texto = buscar.getText().trim();
            sorter.setRowFilter(texto.isEmpty() ? null :
                    RowFilter.regexFilter("(?i)" + Pattern.quote(texto)));
        }
    }

    private void exportar() {
        try {
            JFileChooser selector = new JFileChooser();
            selector.setSelectedFile(new File(String.format("Reporte_%02d.pdf",
                    informe.getSelectedIndex() + 1)));
            if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            List<String> lineas = new ArrayList<>();
            if (tabla.getRowCount() == 0) {
                lineas.add("Sin resultados con los filtros y datos actuales.");
            }
            for (int r = 0; r < tabla.getRowCount(); r++) {
                StringJoiner sj = new StringJoiner(" | ");
                for (int k = 0; k < tabla.getColumnCount(); k++) {
                    Object dato = tabla.getValueAt(r, k);
                    sj.add(tabla.getColumnName(k) + ": " + (dato == null ? "" : dato));
                }
                lineas.add(sj.toString());
             }
            PdfSimple.guardar(selector.getSelectedFile(),
                    String.valueOf(informe.getSelectedItem()), lineas);
            JOptionPane.showMessageDialog(this, "Reporte exportado a PDF.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "No se pudo exportar: " + e.getMessage());
        }
    }
}
