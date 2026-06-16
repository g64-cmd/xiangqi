package com.xiangqi.app.engine

/**
 * 引擎无法启动或运行中崩溃(子进程退出 / IOException / 子进程 native crash 等)。
 *
 * 抛出方:[PikafishEngine]、[SelfEngine](理论上不会,自研纯 Kotlin,但保留接口)。
 * 捕获方:[GameViewModel.launchEngine] catch 后 emit toast,不让异常逃逸到
 * CoroutineExceptionHandler 导致 app 闪退。
 *
 * 这是 RuntimeException(非 checked),因为引擎崩溃是运行时故障,调用方无法
 * 通过类型签名预知;但 ViewModel 显式 try-catch 把它降级为 toast。
 */
class EngineUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
