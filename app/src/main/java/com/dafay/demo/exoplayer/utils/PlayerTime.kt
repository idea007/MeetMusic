package com.dafay.demo.exoplayer.utils


val durationformatshort = "%2\$02d:%3\$02d"
val durationformatlong = "%1\$02d:%2\$02d:%3\$02d"

// 毫秒
fun Int.toPlayerTime(): String {
    if (this <= 0) return "00:00"
    var seconds = this / 1000
    var hours = seconds / 3600
    seconds = seconds % 3600
    var minutes = seconds / 60
    seconds %= 60
    val formatString = if (hours <= 0L) {
        durationformatshort
    } else {
        durationformatlong
    }
    return String.format(formatString, hours, minutes, seconds)
}

fun Long.toPlayerTime(): String {
    if (this <= 0) return "00:00"
    var seconds = this / 1000
    var hours = seconds / 3600
    seconds = seconds % 3600
    var minutes = seconds / 60
    seconds %= 60
    val formatString = if (hours <= 0L) {
        durationformatshort
    } else {
        durationformatlong
    }
    return String.format(formatString, hours, minutes, seconds)
}