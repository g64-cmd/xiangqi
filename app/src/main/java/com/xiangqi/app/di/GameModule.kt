package com.xiangqi.app.di

import com.xiangqi.app.data.game.GameRepository
import com.xiangqi.app.domain.eval.Evaluation
import com.xiangqi.app.domain.movegen.MoveGenerator
import com.xiangqi.app.domain.movegen.MoveGeneratorImpl
import com.xiangqi.app.domain.rules.CheckDetector
import com.xiangqi.app.domain.rules.CheckmateDetector
import com.xiangqi.app.domain.rules.MoveLegality
import com.xiangqi.app.engine.Engine
import com.xiangqi.app.engine.self.MoveOrdering
import com.xiangqi.app.engine.self.SelfEngine
import com.xiangqi.app.engine.self.TranspositionTable
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 第一个 Hilt 模块。
 *
 * 提供 `domain/rules` 与 `domain/movegen` 的无状态规则组件。
 *
 * 显式传所有依赖,避免 Hilt 走带默认参数的构造器(否则 `MoveLegality(gen)` 会自带
 * 一个新 `CheckDetector`,与单例不一致)。所有组件标 `@Singleton`,跨 Repository /
 * ViewModel 共享。
 */
@Module
@InstallIn(SingletonComponent::class)
object GameModule {

    @Provides
    @Singleton
    fun provideMoveGenerator(): MoveGenerator = MoveGeneratorImpl()

    @Provides
    @Singleton
    fun provideCheckDetector(gen: MoveGenerator): CheckDetector = CheckDetector(gen)

    @Provides
    @Singleton
    fun provideMoveLegality(
        gen: MoveGenerator,
        check: CheckDetector,
    ): MoveLegality = MoveLegality(gen, check)

    @Provides
    @Singleton
    fun provideCheckmateDetector(
        gen: MoveGenerator,
        check: CheckDetector,
        legality: MoveLegality,
    ): CheckmateDetector = CheckmateDetector(gen, check, legality)

    @Provides
    @Singleton
    fun provideGameRepository(
        gen: MoveGenerator,
        legality: MoveLegality,
        checkmate: CheckmateDetector,
    ): GameRepository = GameRepository(gen, legality, checkmate)

    @Provides
    @Singleton
    fun provideEvaluation(): Evaluation = Evaluation()

    @Provides
    @Singleton
    fun provideMoveOrdering(
        gen: MoveGenerator,
        checkDetector: CheckDetector,
    ): MoveOrdering = MoveOrdering(gen, checkDetector)

    @Provides
    @Singleton
    fun provideTranspositionTable(): TranspositionTable = TranspositionTable(1 shl 18)

    /**
     * 把 [SelfEngine] 绑定为 [Engine] 接口。M5 接 PikafishEngine 时按 @Qualifier 切换。
     * 标 @Singleton 以复用 256K 槽的 TranspositionTable。
     */
    @Provides
    @Singleton
    fun provideSelfEngine(
        gen: MoveGenerator,
        legality: MoveLegality,
        evaluation: Evaluation,
        checkDetector: CheckDetector,
        checkmate: CheckmateDetector,
        moveOrdering: MoveOrdering,
        tt: TranspositionTable,
    ): Engine = SelfEngine(gen, legality, evaluation, checkDetector, checkmate, moveOrdering, tt)
}
