// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Hilt + KSP 推迟到独立 PR,待 Hilt 发布兼容 AGP 9 的稳定版本后接入
    // alias(libs.plugins.hilt) apply false
    // alias(libs.plugins.ksp) apply false
}
