package dev.yowkey.portfolio.data

import dev.yowkey.portfolio.model.Portfolio
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface PortfolioRepository {
    suspend fun load(): Portfolio
}

/** Reads the bundled portfolio.json; [readContent] is `Res.readBytes` in the app and a fake in tests. */
class ResourcePortfolioRepository(
    private val readContent: suspend () -> ByteArray,
    private val dispatcher: CoroutineDispatcher,
) : PortfolioRepository {

    override suspend fun load(): Portfolio = withContext(dispatcher) {
        PortfolioParser.parse(readContent().decodeToString())
    }
}
