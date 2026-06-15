package com.xiangqi.app.di

import javax.inject.Qualifier

/** 标注自研引擎 [com.xiangqi.app.engine.self.SelfEngine] 的绑定。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
annotation class SelfEngineQual

/** 标注皮卡鱼引擎 [com.xiangqi.app.engine.pikafish.PikafishEngine] 的绑定。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
annotation class PikafishEngineQual
