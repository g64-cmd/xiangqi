package com.xiangqi.app.engine

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
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
 * - [analyze] 为局势评估,默认实现走 `search(ELEMENTARY)` 抽 score;皮卡鱼引擎可覆盖
 *   为发 `eval` 命令做 NNUE 静态评估(瞬时,不搜索)。
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

    /**
     * 局势评估。返回 [sideToMove] 视角下的分数(centipawn)与 mate 标记。
     *
     * 走 [search] + [Difficulty.ANALYZE] 拿 result.score 转 [AnalysisScore]。
     * 用搜索而非静态 eval:静态 eval(NNUE)只看子力配置,不预判后续回合;
     * 玩家吃子后静态 eval 会把短期占优当全局优势,但后续兑子/反扑后可能仍劣势,
     * 真实局势必须靠搜索才能反映。ANALYZE 是皮卡鱼 movetime=3000ms / 自研深度=12
     * 的内部档,玩家有耐心接受延迟换取精度。
     *
     * @return 评估结果。出错时调用方应自行兜底(不抛异常)。
     */
    suspend fun analyze(board: Board, sideToMove: Side): AnalysisScore =
        analyze(board, sideToMove, Difficulty.ANALYZE)

    /**
     * 局势评估(指定难度)。auto-eval 走 ANALYZE 深档;
     * Hint 应着候选评估用 HINT 浅档。
     */
    suspend fun analyze(
        board: Board,
        sideToMove: Side,
        difficulty: Difficulty,
    ): AnalysisScore {
        val result = search(board, sideToMove, difficulty)
        return AnalysisScore(
            scoreCp = result.score.toFloat(),
            isMate = result.isMate,
            matePlies = result.mateInPlies,
        )
    }

    /**
     * 返回当前局面下的 top-N 候选应着(用于 Hint 按钮)。
     *
     * 默认实现走 [search] 拿单 bestmove 兜底;皮卡鱼用 MultiPV 输出多条 PV,
     * 自研引擎走 [com.xiangqi.app.engine.self.Search.searchRootTopN] 收集 root 各走法
     * 分数后取 top-N。
     *
     * 候选**不显示分数**:走完任何候选后,UI 按 auto-eval 流程刷新真实局势分数。
     */
    suspend fun hintCandidates(
        board: Board,
        sideToMove: Side,
        n: Int = 3,
    ): List<Move> {
        val result = search(board, sideToMove, Difficulty.HINT)
        return listOf(result.bestMove)
    }
}

/**
 * 局势评估结果(M6 起)。
 *
 * @property scoreCp sideToMove 视角下的分数(centipawn);调用方需自行做 POV 规范化。
 * @property isMate 是否为杀棋分(皮卡鱼 eval 命令永远 false)。
 * @property matePlies 仅当 [isMate] 为 true 时有意义;距离将死的半回合数。
 */
data class AnalysisScore(
    val scoreCp: Float,
    val isMate: Boolean,
    val matePlies: Int?,
)
