package com.noveltoon.app.util

object TimeFormat {
    fun formatReadingTime(ms: Long): String {
        if (ms <= 0) return "0分钟"
        val totalMinutes = ms / 1000 / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val days = hours / 24
        return when {
            days > 0 -> "${days}天${hours % 24}小时"
            hours > 0 -> "${hours}小时${minutes}分钟"
            else -> "${minutes}分钟"
        }
    }
}
