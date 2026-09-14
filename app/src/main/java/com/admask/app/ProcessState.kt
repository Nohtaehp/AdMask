package com.admask.app

/**
 * 进程级状态标记。
 *
 * 静态变量随进程一起消失，因此可以用它判断"本次是否属于冷启动"：
 * 首次打开应用、或进程被系统 / 用户杀掉后重建，都算冷启动。
 * 同一进程内反复进出设置界面不算。
 */
object ProcessState {

    @Volatile
    private var reconciled = false

    /** 当前进程是否已经完成过一次状态校正 */
    fun isColdStart(): Boolean = !reconciled

    fun markReconciled() {
        reconciled = true
    }
}
