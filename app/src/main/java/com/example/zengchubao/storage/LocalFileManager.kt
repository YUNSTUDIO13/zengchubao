package com.example.zengchubao.storage

import android.content.Context
import com.example.zengchubao.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LocalFileManager(private val context: Context) {

    // 内部测试用构造器
    internal constructor(testDir: File) : this(context = null!!) {
        _baseDir = testDir
    }

    private var _baseDir: File? = null
    private val baseDir: File
        get() = _baseDir ?: File(context.filesDir, "zengchubao_data").also {
            if (!it.exists()) it.mkdirs()
            _baseDir = it
        }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
    }

    // ── 文件路径 ──
    private val depositsFile get() = File(baseDir, "deposits.json")
    private val banksFile get() = File(baseDir, "banks.json")
    private val productsFile get() = File(baseDir, "products.json")
    private val archiveFile get() = File(baseDir, "archive_records.json")
    private val settingsFile get() = File(baseDir, "settings.json")
    private val initFlagFile get() = File(baseDir, ".initialized")

    // ── 初始化 ──

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (!baseDir.exists()) baseDir.mkdirs()
        if (!initFlagFile.exists()) {
            // 首次运行，写入预置数据
            saveBanks(PRESET_BANKS)
            saveProducts(PRESET_PRODUCTS)
            saveDeposits(emptyList())
            saveArchiveRecords(emptyList())
            saveSettings(AppSettings())
            initFlagFile.createNewFile()
        } else {
            // 确保所有文件存在
            if (!depositsFile.exists()) saveDeposits(emptyList())
            if (!banksFile.exists()) saveBanks(PRESET_BANKS)
            if (!productsFile.exists()) saveProducts(PRESET_PRODUCTS)
            if (!archiveFile.exists()) saveArchiveRecords(emptyList())
            if (!settingsFile.exists()) saveSettings(AppSettings())
        }
    }

    // ── 存单 CRUD ──

    suspend fun getAllDeposits(): List<Deposit> = withContext(Dispatchers.IO) {
        if (!depositsFile.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<List<Deposit>>(depositsFile.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getHoldingDeposits(): List<Deposit> = withContext(Dispatchers.IO) {
        getAllDeposits().filter { it.status == DepositStatus.HOLDING }
    }

    suspend fun getArchivedDeposits(): List<Deposit> = withContext(Dispatchers.IO) {
        getAllDeposits().filter { it.status == DepositStatus.ARCHIVED }
    }

    suspend fun getDepositById(id: String): Deposit? = withContext(Dispatchers.IO) {
        getAllDeposits().find { it.id == id }
    }

    suspend fun saveDeposit(deposit: Deposit) = withContext(Dispatchers.IO) {
        val deposits = getAllDeposits().toMutableList()
        val index = deposits.indexOfFirst { it.id == deposit.id }
        if (index >= 0) {
            deposits[index] = deposit
        } else {
            deposits.add(deposit)
        }
        saveDeposits(deposits)
    }

    suspend fun deleteDeposit(id: String) = withContext(Dispatchers.IO) {
        val deposits = getAllDeposits().filter { it.id != id }
        saveDeposits(deposits)
    }

    suspend fun archiveDeposit(id: String) = withContext(Dispatchers.IO) {
        val deposits = getAllDeposits().toMutableList()
        val index = deposits.indexOfFirst { it.id == id }
        if (index >= 0) {
            deposits[index] = deposits[index].copy(
                status = DepositStatus.ARCHIVED,
                updatedAt = System.currentTimeMillis()
            )
            saveDeposits(deposits)
        }
    }

    /** 到期自动归档：到期后第一天即自动归档 */
    suspend fun autoArchiveExpired() = withContext(Dispatchers.IO) {
        val deposits = getAllDeposits().toMutableList()
        val today = todayString()
        var changed = false
        deposits.forEachIndexed { index, deposit ->
            if (deposit.status == DepositStatus.HOLDING || deposit.status == DepositStatus.MATURED) {
                if (deposit.endDate < today) {
                    deposits[index] = deposit.copy(
                        status = DepositStatus.ARCHIVED,
                        updatedAt = System.currentTimeMillis()
                    )
                    changed = true
                }
            }
        }
        if (changed) saveDeposits(deposits)
    }

    private suspend fun saveDeposits(deposits: List<Deposit>) = withContext(Dispatchers.IO) {
        depositsFile.writeText(json.encodeToString(deposits))
    }

    // ── 银行 CRUD ──

    suspend fun getAllBanks(): List<Bank> = withContext(Dispatchers.IO) {
        if (!banksFile.exists()) return@withContext PRESET_BANKS
        try {
            json.decodeFromString<List<Bank>>(banksFile.readText())
        } catch (e: Exception) {
            PRESET_BANKS
        }
    }

    suspend fun addBank(bank: Bank) = withContext(Dispatchers.IO) {
        val banks = getAllBanks().toMutableList()
        banks.add(bank)
        saveBanks(banks)
    }

    suspend fun deleteBank(id: String) = withContext(Dispatchers.IO) {
        val banks = getAllBanks().filter { it.id != id }
        saveBanks(banks)
        // 同时删除关联产品
        val products = getAllProducts().filter { it.bankId != id }
        saveProducts(products)
    }

    suspend fun updateBankColor(bankId: String, colorHex: String) = withContext(Dispatchers.IO) {
        val banks = getAllBanks().toMutableList()
        val idx = banks.indexOfFirst { it.id == bankId }
        if (idx >= 0) {
            banks[idx] = banks[idx].copy(colorHex = colorHex)
            saveBanks(banks)
        }
    }

    private suspend fun saveBanks(banks: List<Bank>) = withContext(Dispatchers.IO) {
        banksFile.writeText(json.encodeToString(banks))
    }

    // ── 产品 CRUD ──

    suspend fun getAllProducts(): List<Product> = withContext(Dispatchers.IO) {
        if (!productsFile.exists()) return@withContext PRESET_PRODUCTS
        try {
            json.decodeFromString<List<Product>>(productsFile.readText())
        } catch (e: Exception) {
            PRESET_PRODUCTS
        }
    }

    suspend fun getProductsByBank(bankId: String): List<Product> = withContext(Dispatchers.IO) {
        getAllProducts().filter { it.bankId == bankId }
    }

    suspend fun addProduct(product: Product) = withContext(Dispatchers.IO) {
        val products = getAllProducts().toMutableList()
        products.add(product)
        saveProducts(products)
    }

    suspend fun deleteProduct(id: String) = withContext(Dispatchers.IO) {
        val products = getAllProducts().filter { it.id != id }
        saveProducts(products)
    }

    private suspend fun saveProducts(products: List<Product>) = withContext(Dispatchers.IO) {
        productsFile.writeText(json.encodeToString(products))
    }

    // ── 归档记录 ──

    suspend fun getAllArchiveRecords(): List<ArchiveRecord> = withContext(Dispatchers.IO) {
        if (!archiveFile.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<List<ArchiveRecord>>(archiveFile.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveArchiveRecord(record: ArchiveRecord) = withContext(Dispatchers.IO) {
        val records = getAllArchiveRecords().toMutableList()
        val index = records.indexOfFirst { it.id == record.id }
        if (index >= 0) records[index] = record else records.add(record)
        saveArchiveRecords(records)
    }

    private suspend fun saveArchiveRecords(records: List<ArchiveRecord>) = withContext(Dispatchers.IO) {
        archiveFile.writeText(json.encodeToString(records))
    }

    // ── 设置 ──

    suspend fun getSettings(): AppSettings = withContext(Dispatchers.IO) {
        if (!settingsFile.exists()) return@withContext AppSettings()
        try {
            val saved = json.decodeFromString<AppSettings>(settingsFile.readText())
            val migrated = saved.migrated()
            // 主动落盘，消除旧配置中的已废弃项
            saveSettings(migrated)
            migrated
        } catch (e: Exception) {
            AppSettings()
        }
    }

    suspend fun saveSettings(settings: AppSettings) = withContext(Dispatchers.IO) {
        settingsFile.writeText(json.encodeToString(settings))
    }

    // ── 数据导出 ──

    suspend fun exportToZip(outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val deposits = getAllDeposits()
            val banks = getAllBanks()
            val products = getAllProducts()
            val archives = getAllArchiveRecords()
            val settings = getSettings()

            val payload = BackupPayload(
                deposits = deposits,
                banks = banks,
                products = products,
                archiveRecords = archives,
                settings = settings
            )

            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                zos.putNextEntry(ZipEntry("data.json"))
                zos.write(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ── 数据导入 ──

    suspend fun importFromZip(zipFile: File, mode: ImportMode = ImportMode.OVERWRITE): ImportResult =
        withContext(Dispatchers.IO) {
            try {
                ZipInputStream(zipFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    var dataJson: String? = null

                    while (entry != null) {
                        if (entry.name == "data.json") {
                            dataJson = zis.readBytes().toString(Charsets.UTF_8)
                            break
                        }
                        entry = zis.nextEntry
                    }

                    if (dataJson == null) {
                        return@withContext ImportResult(false, "数据包结构不完整：缺少data.json")
                    }

                    val payload = try {
                        json.decodeFromString<BackupPayload>(dataJson)
                    } catch (e: Exception) {
                        return@withContext ImportResult(false, "数据文件已损坏：${e.message}")
                    }

                    when (mode) {
                        ImportMode.OVERWRITE -> {
                            saveDeposits(payload.deposits)
                            saveBanks(payload.banks)
                            saveProducts(payload.products)
                            saveArchiveRecords(payload.archiveRecords)
                            saveSettings(payload.settings)
                        }
                        ImportMode.MERGE -> {
                            // 合并：按updatedAt保留最新
                            val existingDeposits = getAllDeposits()
                            val mergedDeposits = mergeByUpdatedAt(existingDeposits, payload.deposits)
                            saveDeposits(mergedDeposits)

                            val existingBanks = getAllBanks()
                            val mergedBanks = existingBanks.toMutableList()
                            payload.banks.forEach { newBank ->
                                if (mergedBanks.none { it.id == newBank.id }) mergedBanks.add(newBank)
                            }
                            saveBanks(mergedBanks)

                            val existingProducts = getAllProducts()
                            val mergedProducts = existingProducts.toMutableList()
                            payload.products.forEach { newProd ->
                                if (mergedProducts.none { it.id == newProd.id }) mergedProducts.add(newProd)
                            }
                            saveProducts(mergedProducts)
                        }
                    }

                    ImportResult(true, "导入成功，共${payload.deposits.size}条存单记录")
                }
            } catch (e: Exception) {
                ImportResult(false, "导入失败：${e.message}")
            }
        }

    private fun mergeByUpdatedAt(existing: List<Deposit>, incoming: List<Deposit>): List<Deposit> {
        val map = existing.associateBy { it.id }.toMutableMap()
        incoming.forEach { dep ->
            val old = map[dep.id]
            if (old == null || dep.updatedAt > old.updatedAt) {
                map[dep.id] = dep
            }
        }
        return map.values.toList()
    }

    enum class ImportMode { OVERWRITE, MERGE }

    data class ImportResult(val success: Boolean, val message: String)
}
