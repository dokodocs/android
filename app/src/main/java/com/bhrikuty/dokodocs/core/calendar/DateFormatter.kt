package com.bhrikuty.dokodocs.core.calendar

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CalendarSystem(val code: String) {
    AD("ad"),
    BS("bs");

    companion object {
        fun fromCode(code: String): CalendarSystem =
            if (code.equals("bs", ignoreCase = true)) BS else AD
    }
}

class DateFormatter(val calendar: CalendarSystem = CalendarSystem.AD) {

    private val adMediumFormat = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
    private val adMediumWithTimeFormat = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH)

    fun medium(timestamp: Long): String = medium(Date(timestamp))

    fun medium(date: Date): String {
        return if (calendar == CalendarSystem.BS) {
            val nepaliDate = NepaliDate.fromDate(date)
            nepaliDate.formatNepali()
        } else {
            adMediumFormat.format(date)
        }
    }

    fun mediumWithTime(timestamp: Long): String = mediumWithTime(Date(timestamp))

    fun mediumWithTime(date: Date): String {
        return if (calendar == CalendarSystem.BS) {
            val nepaliDate = NepaliDate.fromDate(date)
            val timeFormat = SimpleDateFormat("h:mm a", Locale.ENGLISH)
            "${nepaliDate.formatNepali()}, ${timeFormat.format(date)}"
        } else {
            adMediumWithTimeFormat.format(date)
        }
    }

    fun shortDate(timestamp: Long): String = shortDate(Date(timestamp))
    fun shortDate(date: Date): String = medium(date)

    fun alwaysAd(timestamp: Long): String = adMediumFormat.format(Date(timestamp))
    fun alwaysAd(date: Date): String = adMediumFormat.format(date)

    val showsSecondaryAd: Boolean
        get() = calendar == CalendarSystem.BS
}
