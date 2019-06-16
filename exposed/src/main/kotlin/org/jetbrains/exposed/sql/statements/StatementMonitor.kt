package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import java.sql.PreparedStatement

interface StatementInterceptor {
    fun beforeExecution(transaction: Transaction, context: StatementContext) {}
    fun afterExecution(transaction: Transaction, contexts: List<StatementContext>, executedStatement: PreparedStatementApi) {}

    fun beforeCommit(transaction: Transaction) {}
    fun afterCommit() {}

    fun beforeRollback(transaction: Transaction) {}
    fun afterRollback() {}
}