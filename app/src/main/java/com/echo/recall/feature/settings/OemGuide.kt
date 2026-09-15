package com.echo.recall.feature.settings

/** 机型保活指引（纯数据，可单测） */
data class OemGuide(
    val brand: String,
    val steps: List<String>,
)

object OemGuides {

    fun forManufacturer(manufacturer: String?): OemGuide = when (manufacturer?.trim()?.lowercase()) {
        "xiaomi", "redmi", "poco" -> OemGuide(
            brand = "小米 / Redmi",
            steps = listOf(
                "设置 → 应用设置 → 应用管理 → 回声 → 省电策略 → 选「无限制」",
                "同上页面 → 自启动 → 打开",
                "同上页面 → 权限管理 → 后台弹出界面 → 允许",
                "最近任务里下拉「回声」卡片 → 点锁图标锁定（防止一键清理）",
            ),
        )

        "huawei", "honor" -> OemGuide(
            brand = "华为 / 荣耀",
            steps = listOf(
                "设置 → 应用 → 应用启动管理 → 回声 → 关闭「自动管理」",
                "手动管理里把「自启动 / 关联启动 / 后台活动」全部打开",
                "设置 → 电池 → 更多电池设置 → 关闭「休眠时始终保持网络连接」限制",
                "最近任务里下拉「回声」卡片 → 点锁图标锁定",
            ),
        )

        "oppo", "realme", "oneplus" -> OemGuide(
            brand = "OPPO / realme / 一加",
            steps = listOf(
                "设置 → 电池 → 应用耗电管理 → 回声 → 允许后台运行 / 允许自启动",
                "设置 → 应用管理 → 回声 → 省电策略 → 不限制",
                "最近任务里下拉「回声」卡片 → 点锁图标锁定",
            ),
        )

        "vivo", "iqoo" -> OemGuide(
            brand = "vivo / iQOO",
            steps = listOf(
                "设置 → 电池 → 后台高耗电 → 允许「回声」",
                "设置 → 应用与权限 → 权限管理 → 自启动 → 允许「回声」",
                "最近任务里下拉「回声」卡片 → 点锁图标锁定",
            ),
        )

        "samsung" -> OemGuide(
            brand = "三星",
            steps = listOf(
                "设置 → 电池和设备维护 → 电池 → 后台使用限制 → 把「回声」加入「不休眠的应用」",
                "设置 → 应用 → 回声 → 电池 → 选「不受限制」",
                "同上页面 → 关闭「让未使用的应用进入休眠」对回声的限制",
                "最近任务 → 点「回声」图标 → 选「保持打开」，避免被清理",
            ),
        )

        "meizu" -> OemGuide(
            brand = "魅族",
            steps = listOf(
                "手机管家 → 权限管理 → 后台管理 → 回声 → 允许后台运行",
                "设置 → 电量管理 → 耗电保护 → 回声 → 允许后台运行",
                "设置 → 应用管理 → 回声 → 自启动 / 关联启动 → 允许",
                "最近任务里下拉「回声」卡片 → 点锁图标锁定",
            ),
        )

        else -> OemGuide(
            brand = "通用设置",
            steps = listOf(
                "设置 → 应用 → 回声 → 电池 → 选「无限制 / 不受限」",
                "允许「回声」自启动与后台运行",
                "在最近任务里锁定「回声」，避免一键清理",
            ),
        )
    }
}
