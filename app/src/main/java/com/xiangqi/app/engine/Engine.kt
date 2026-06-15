package com.xiangqi.app.engine

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Side
import kotlinx.coroutines.flow.StateFlow

/**
 * 象棋引擎抽象。自研引擎(M2)与皮卡鱼引擎(M5)各自实现此接口,
 * 供 GameViewModel 按难度档位透明切换。
 *
 * 调用约定:
 * - [search] 为 `suspend` 函数,内部协程上下文负责取消检查(每 N 节点 ensureActive)。
 * - 调用方在外部 `Job.cancel()` 即可中止搜索;若首次迭代未完成,search 会透传 CancellationException。
 * - [info] 在每次迭代加深完成时更新,供 UI 实时显示思考过程。
 */
interface Engine {

    /** 引擎实现类型。 */
    val type: EngineType

    /**
     * 思考过程流。搜索开始前为 null;
     * 每次迭代加深完成后推送一个新的 [SearchInfo] 快照。
     */
    val info: StateFlow<SearchInfo?>

    /**
     * 在 [board] 上为 [sideToMove] 方搜索最佳走法。
     *
     * @param board 当前局面。
     * @param sideToMove 当前走子方。
     * @param difficulty 难度档位(决定深度与 movetime)。
     * @return 搜索结果,包含 bestMove / score / pv 等。
     */
    suspend fun search(
        board: Board,
        sideToMove: Side,
        difficulty: Difficulty,
    ): EngineResult
}
