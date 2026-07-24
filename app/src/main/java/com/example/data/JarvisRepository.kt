package com.example.data

import kotlinx.coroutines.flow.Flow

class JarvisRepository(
    private val automationDao: AutomationDao,
    private val executionLogDao: ExecutionLogDao,
    private val memoryDao: MemoryDao
) {
    val allRules: Flow<List<AutomationRule>> = automationDao.getAllRules()
    val recentLogs: Flow<List<ExecutionLog>> = executionLogDao.getRecentLogs()
    val allMemories: Flow<List<AgentMemory>> = memoryDao.getAllMemories()

    suspend fun insertRule(rule: AutomationRule): Long = automationDao.insertRule(rule)
    suspend fun updateRule(rule: AutomationRule) = automationDao.updateRule(rule)
    suspend fun deleteRule(rule: AutomationRule) = automationDao.deleteRule(rule)
    suspend fun deleteRuleById(id: Long) = automationDao.deleteRuleById(id)
    suspend fun getEnabledRules(): List<AutomationRule> = automationDao.getEnabledRules()

    suspend fun logExecution(title: String, description: String, status: String = "SUCCESS") {
        executionLogDao.insertLog(
            ExecutionLog(
                ruleOrCommandTitle = title,
                description = description,
                status = status
            )
        )
    }

    suspend fun clearLogs() = executionLogDao.clearLogs()

    suspend fun saveMemory(key: String, value: String) {
        memoryDao.saveMemory(AgentMemory(key = key, value = value))
    }

    suspend fun getMemory(key: String): String? = memoryDao.getMemory(key)?.value
}
