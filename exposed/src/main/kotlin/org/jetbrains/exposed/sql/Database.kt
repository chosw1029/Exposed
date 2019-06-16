package org.jetbrains.exposed.sql

import org.h2.jdbc.JdbcDatabaseMetaData
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.sql.statements.jdbc.JdbcDatabaseMetadataImpl
import org.jetbrains.exposed.sql.transactions.DEFAULT_ISOLATION_LEVEL
import org.jetbrains.exposed.sql.transactions.DEFAULT_REPETITION_ATTEMPTS
import org.jetbrains.exposed.sql.transactions.ThreadLocalTransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

class Database private constructor(val connector: () -> Connection) {

    internal fun <T> metadata(body: ExposedDatabaseMetadata.() -> T) : T {
        val transaction = TransactionManager.currentOrNull()
        return if (transaction == null) {
            val connection = connector()
            try {
                JdbcDatabaseMetadataImpl(connection.metaData).body()
            } finally {
                connection.close()
            }
        } else
            JdbcDatabaseMetadataImpl(transaction.connection as JdbcConnectionImpl).metaData).body()
    }

    val url: String by lazy { metadata { url } }

    val dialect by lazy {
        val name = url.removePrefix("jdbc:").substringBefore(':')
        dialects[name.toLowerCase()]?.invoke() ?: error("No dialect registered for $name. URL=$url")
    }

    val vendor: String get() = dialect.name

    val version by lazy { metadata { version } }

    fun isVersionCovers(version: BigDecimal) = this.version >= version

    val supportsAlterTableWithAddColumn by lazy(LazyThreadSafetyMode.NONE) { metadata { supportsAlterTableWithAddColumn } }
    val supportsMultipleResultSets by lazy(LazyThreadSafetyMode.NONE) { metadata { supportsMultipleResultSets } }

    internal val identifierManager by lazy { metadata { identifierManager } }


    var defaultFetchSize: Int? = null
        private set

    fun defaultFetchSize(size: Int): Database {
        defaultFetchSize = size
        return this
    }

    companion object {
        private val dialects = ConcurrentHashMap<String, () ->DatabaseDialect>()

        init {
            registerDialect(H2Dialect.dialectName) { H2Dialect() }
            registerDialect(MysqlDialect.dialectName) { MysqlDialect() }
            registerDialect(PostgreSQLDialect.dialectName) { PostgreSQLDialect() }
            registerDialect(SQLiteDialect.dialectName) { SQLiteDialect() }
            registerDialect(OracleDialect.dialectName) { OracleDialect() }
            registerDialect(SQLServerDialect.dialectName) { SQLServerDialect() }
            registerDialect(MariaDBDialect.dialectName) { MariaDBDialect() }
        }

        fun registerDialect(prefix:String, dialect: () -> DatabaseDialect) {
            dialects[prefix] = dialect
        }

        private fun doConnect(getNewConnection: () -> Connection, setupConnection: (Connection) -> Unit = {},
                    manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_ISOLATION_LEVEL, DEFAULT_REPETITION_ATTEMPTS) }
        ): Database {
            return Database {
                getNewConnection().apply { setupConnection(this) }
            }.apply {
                TransactionManager.registerManager(this, manager(this))
            }
        }

        fun connect(datasource: DataSource, setupConnection: (Connection) -> Unit = {},
                    manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_ISOLATION_LEVEL, DEFAULT_REPETITION_ATTEMPTS) }
        ): Database {
            return doConnect( { datasource.connection!! }, setupConnection, manager )
        }

        fun connect(getNewConnection: () -> Connection,
                    manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_ISOLATION_LEVEL, DEFAULT_REPETITION_ATTEMPTS) }
        ): Database {
            return doConnect( getNewConnection, manager = manager )
        }
        fun connect(url: String, driver: String, user: String = "", password: String = "", setupConnection: (Connection) -> Unit = {},
                    manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_ISOLATION_LEVEL, DEFAULT_REPETITION_ATTEMPTS) }): Database {
            Class.forName(driver).newInstance()

            return doConnect( { DriverManager.getConnection(url, user, password) }, setupConnection, manager )
        }
    }
}

val Database.name : String get() = url.substringAfterLast('/').substringBefore('?')
