package com.example.inventory.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject

@Database(
    entities = [EquipoEntity::class, SalaEntity::class, EquipoComputoEntity::class],
    version = 8,
    exportSchema = true, // el esquema se guarda en app/schemas (ver ksp en build.gradle.kts)
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun equipoDao(): EquipoDao
    abstract fun salaDao(): SalaDao
    abstract fun equipoComputoDao(): EquipoComputoDao

    companion object {
        const val NOMBRE = "inventario.db"
    }
}

/**
 * Migraciones. Sustituyen a fallbackToDestructiveMigration, que BORRABA la base local
 * (y los cambios sin sincronizar) en cada cambio de versión.
 */
object Migraciones {

    /**
     * 6 → 7: de las tablas normalizadas (articulos, marcas, modelos, equipos, equipos_computo)
     * a una sola tabla equipos con columnas de sincronización.
     *
     * - Se conservan todos los equipos. Como nunca se habían sincronizado, quedan como PENDIENTE_CREAR.
     * - "SIN ARTICULO / SIN MARCA / SIN MODELO" pasan a los valores por defecto del backend.
     * - La ubicación se eliminó de la app; si un equipo la tenía, se agrega al final de
     *   Observaciones ("Ubicación: ...") para no perder el dato.
     * - equipos_computo no se usaba (la pantalla está pendiente) y se elimina.
     */
    val MIGRACION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `equipos_nueva` (" +
                    "`inventario` TEXT NOT NULL, `serie` TEXT NOT NULL, `articulo` TEXT NOT NULL, " +
                    "`marca` TEXT NOT NULL, `modelo` TEXT NOT NULL, `caracteristicas` TEXT NOT NULL, " +
                    "`estado` TEXT NOT NULL, `fecha_registro` INTEGER NOT NULL, " +
                    "`fecha_actualizacion_local` INTEGER NOT NULL, `fecha_actualizacion_servidor` TEXT, " +
                    "`existe_en_servidor` INTEGER NOT NULL, `eliminado` INTEGER NOT NULL, " +
                    "`estado_sync` TEXT NOT NULL, `intentos` INTEGER NOT NULL, `ultimo_error` TEXT, " +
                    "`version_servidor` TEXT, PRIMARY KEY(`inventario`))"
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO equipos_nueva (inventario, serie, articulo, marca, modelo, caracteristicas,
                    estado, fecha_registro, fecha_actualizacion_local, fecha_actualizacion_servidor,
                    existe_en_servidor, eliminado, estado_sync, intentos, ultimo_error, version_servidor)
                SELECT
                    TRIM(e.inventario),
                    COALESCE(TRIM(e.serie), ''),
                    CASE WHEN a.nombre_articulo IS NULL OR TRIM(a.nombre_articulo) = '' OR a.nombre_articulo = 'SIN ARTICULO'
                         THEN 'ARTÍCULO GENERAL' ELSE UPPER(TRIM(a.nombre_articulo)) END,
                    CASE WHEN ma.nombre_marca IS NULL OR TRIM(ma.nombre_marca) = '' OR ma.nombre_marca = 'SIN MARCA'
                         THEN 'GENÉRICA' ELSE UPPER(TRIM(ma.nombre_marca)) END,
                    CASE WHEN mo.nombre_modelo IS NULL OR TRIM(mo.nombre_modelo) = '' OR mo.nombre_modelo = 'SIN MODELO'
                         THEN 'GENÉRICO' ELSE UPPER(TRIM(mo.nombre_modelo)) END,
                    TRIM(
                        COALESCE(TRIM(e.caracteristicas), '') ||
                        CASE WHEN TRIM(COALESCE(e.ubicacion, '')) <> ''
                             THEN (CASE WHEN TRIM(COALESCE(e.caracteristicas, '')) <> '' THEN char(10) ELSE '' END)
                                  || 'Ubicación: ' || TRIM(e.ubicacion)
                             ELSE '' END
                    ),
                    CASE WHEN UPPER(e.estado) IN ('ALTA', 'MANTENIMIENTO', 'BAJA') THEN UPPER(e.estado) ELSE 'ALTA' END,
                    e.fecha_registro,
                    e.fecha_actualizacion,
                    NULL, 0, 0, 'PENDIENTE_CREAR', 0, NULL, NULL
                FROM equipos e
                LEFT JOIN articulos a ON a.id_articulo = e.id_articulo
                LEFT JOIN modelos mo ON mo.id_modelo = e.id_modelo
                LEFT JOIN marcas ma ON ma.id_marca = mo.id_marca
                WHERE TRIM(e.inventario) <> ''
                """.trimIndent()
            )
            // Primero las tablas hijas (por las llaves foráneas)
            db.execSQL("DROP TABLE IF EXISTS `equipos_computo`")
            db.execSQL("DROP TABLE IF EXISTS `equipos`")
            db.execSQL("DROP TABLE IF EXISTS `modelos`")
            db.execSQL("DROP TABLE IF EXISTS `marcas`")
            db.execSQL("DROP TABLE IF EXISTS `articulos`")
            db.execSQL("ALTER TABLE `equipos_nueva` RENAME TO `equipos`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_equipos_estado_sync` ON `equipos` (`estado_sync`)")
        }
    }

    /**
     * 7 → 8: tablas para la pantalla de equipos de cómputo (salas y equipos_computo).
     * No toca la tabla equipos. El SQL debe coincidir exactamente con lo que genera Room
     * para [SalaEntity] y [EquipoComputoEntity] (se puede comparar con app/schemas/.../8.json).
     */
    val MIGRACION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `salas` (`id_sala` INTEGER NOT NULL, `codigo` TEXT NOT NULL, " +
                    "`nombre` TEXT NOT NULL, `ubicacion` TEXT NOT NULL, PRIMARY KEY(`id_sala`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `equipos_computo` (`inventario` TEXT NOT NULL, `id_servidor` INTEGER, " +
                    "`serie` TEXT NOT NULL, `articulo` TEXT NOT NULL, `marca` TEXT NOT NULL, `modelo` TEXT NOT NULL, " +
                    "`caracteristicas` TEXT NOT NULL, `estado` TEXT NOT NULL, `id_sala` INTEGER, " +
                    "`procesador` TEXT NOT NULL, `ram` TEXT NOT NULL, `almacenamiento` TEXT NOT NULL, " +
                    "`especificaciones_hardware` TEXT NOT NULL, `sistema_operativo` TEXT NOT NULL, " +
                    "`software_instalado` TEXT NOT NULL, `estado_computadora` TEXT NOT NULL, `estado_cpu` TEXT NOT NULL, " +
                    "`estado_monitor` TEXT NOT NULL, `estado_teclado` TEXT NOT NULL, `estado_mouse` TEXT NOT NULL, " +
                    "`estado_cable_ethernet` TEXT NOT NULL, `observaciones` TEXT NOT NULL, " +
                    "`tiene_contrasena` INTEGER NOT NULL, `tiene_ultima_contrasena` INTEGER NOT NULL, " +
                    "`contrasena_pendiente` TEXT, `borrar_contrasena` INTEGER NOT NULL, `motivo_cambio` TEXT, " +
                    "`actualizado_por` TEXT, `fecha_registro` INTEGER NOT NULL, `fecha_actualizacion_local` INTEGER NOT NULL, " +
                    "`fecha_actualizacion_servidor` TEXT, `existe_en_servidor` INTEGER NOT NULL, `estado_sync` TEXT NOT NULL, " +
                    "`intentos` INTEGER NOT NULL, `ultimo_error` TEXT, `version_servidor` TEXT, PRIMARY KEY(`inventario`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_equipos_computo_id_sala` ON `equipos_computo` (`id_sala`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_equipos_computo_estado_sync` ON `equipos_computo` (`estado_sync`)")
        }
    }
}

/**
 * Ejecuta un bloque dentro de una transacción. Es una interfaz para que el código que la usa
 * (repositorio, sincronización) se pueda probar sin una base de datos real.
 */
interface EjecutorTransacciones {
    suspend fun <T> ejecutar(bloque: suspend () -> T): T
}

class EjecutorTransaccionesRoom @Inject constructor(
    private val db: AppDatabase,
) : EjecutorTransacciones {
    override suspend fun <T> ejecutar(bloque: suspend () -> T): T = db.withTransaction { bloque() }
}
