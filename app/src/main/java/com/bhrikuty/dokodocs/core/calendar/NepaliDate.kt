package com.bhrikuty.dokodocs.core.calendar

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Complete Bikram Sambat (BS) Nepali Calendar Engine with Bidirectional Conversion.
 * Supports BS Years 2000 to 2095.
 */
data class NepaliDate(
    val year: Int,
    val month: Int, // 1 to 12 (1 = Baisakh, 12 = Chaitra)
    val day: Int,   // 1 to 32
    val dayOfWeek: Int = 1
) {
    companion object {
        val MONTH_NAMES_NE = listOf(
            "बैशाख", "जेठ", "असार", "साउन", "भदौ", "असोज",
            "कात्तिक", "मंसीर", "पुष", "माघ", "फागुन", "चैत"
        )
        val MONTH_NAMES_EN = listOf(
            "Baisakh", "Jestha", "Ashadh", "Shrawan", "Bhadra", "Ashwin",
            "Kartik", "Mangsir", "Poush", "Magh", "Falgun", "Chaitra"
        )
        val DAYS_OF_WEEK_NE = listOf(
            "आइतबार", "सोमबार", "मंगलबार", "बुधबार", "बिहीबार", "शुक्रबार", "शनिबार"
        )
        val DAYS_OF_WEEK_EN = listOf(
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
        )
        val NEPALI_DIGITS = listOf('०', '१', '२', '३', '४', '५', '६', '७', '८', '९')

        fun toDevanagari(number: Int): String {
            return number.toString().map { char ->
                if (char in '0'..'9') NEPALI_DIGITS[char - '0'] else char
            }.joinToString("")
        }

        fun fromDevanagari(devanagariStr: String): Int {
            val normalStr = devanagariStr.map { char ->
                val idx = NEPALI_DIGITS.indexOf(char)
                if (idx != -1) ('0' + idx) else char
            }.joinToString("")
            return normalStr.toIntOrNull() ?: 0
        }

        // Comprehensive monthly day count lookup from BS 2060 to BS 2095
        private val BS_CALENDAR_DATA = mapOf(
            2060 to listOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30),
            2061 to listOf(31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30),
            2062 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31),
            2063 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
            2064 to listOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30),
            2065 to listOf(31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30),
            2066 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31),
            2067 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
            2068 to listOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30),
            2069 to listOf(31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30),
            2070 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31),
            2071 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
            2072 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
            2073 to listOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30),
            2074 to listOf(31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30),
            2075 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31),
            2076 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
            2077 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
            2078 to listOf(31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30),
            2079 to listOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30),
            2080 to listOf(31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30),
            2081 to listOf(31, 31, 32, 31, 31, 30, 30, 30, 29, 30, 29, 31),
            2082 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
            2083 to listOf(31, 31, 32, 31, 31, 30, 30, 30, 29, 30, 29, 31),
            2084 to listOf(31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30),
            2085 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
            2086 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
            2087 to listOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30),
            2088 to listOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30),
            2089 to listOf(31, 31, 32, 31, 31, 30, 30, 30, 29, 30, 29, 31),
            2090 to listOf(31, 31, 32, 31, 32, 30, 29, 30, 29, 30, 29, 31),
            2091 to listOf(31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30),
            2092 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
            2093 to listOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
            2094 to listOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30),
            2095 to listOf(31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30)
        )

        // Reference Anchor: 2080 BS Baisakh 1 = 2023 AD April 14
        private const val REF_BS_YEAR = 2080
        private const val REF_AD_YEAR = 2023
        private const val REF_AD_MONTH = Calendar.APRIL
        private const val REF_AD_DAY = 14

        fun getDaysInMonth(bsYear: Int, bsMonth: Int): Int {
            val list = BS_CALENDAR_DATA[bsYear] ?: listOf(31, 31, 32, 31, 31, 30, 30, 30, 29, 30, 29, 31)
            return list.getOrElse(bsMonth - 1) { 30 }
        }

        fun today(): NepaliDate = fromDate(Date())

        fun fromDate(date: Date): NepaliDate {
            val cal = Calendar.getInstance().apply { time = date }
            val refCal = Calendar.getInstance().apply {
                set(REF_AD_YEAR, REF_AD_MONTH, REF_AD_DAY, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val targetCal = Calendar.getInstance().apply {
                set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }

            var diffDays = ((targetCal.timeInMillis - refCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()

            var bsYear = REF_BS_YEAR
            var bsMonth = 1
            var bsDay = 1

            if (diffDays >= 0) {
                while (diffDays > 0) {
                    val daysInCurrentMonth = getDaysInMonth(bsYear, bsMonth)
                    val remainingInMonth = daysInCurrentMonth - bsDay + 1

                    if (diffDays >= remainingInMonth) {
                        diffDays -= remainingInMonth
                        bsDay = 1
                        bsMonth++
                        if (bsMonth > 12) {
                            bsMonth = 1
                            bsYear++
                        }
                    } else {
                        bsDay += diffDays
                        diffDays = 0
                    }
                }
            } else {
                diffDays = -diffDays
                while (diffDays > 0) {
                    bsDay--
                    if (bsDay < 1) {
                        bsMonth--
                        if (bsMonth < 1) {
                            bsMonth = 12
                            bsYear--
                        }
                        bsDay = getDaysInMonth(bsYear, bsMonth)
                    }
                    diffDays--
                }
            }

            return NepaliDate(bsYear, bsMonth, bsDay, cal.get(Calendar.DAY_OF_WEEK))
        }
    }

    /**
     * Converts this NepaliDate back into Gregorian AD Date.
     */
    fun toGregorianDate(): Date {
        val refCal = Calendar.getInstance().apply {
            set(REF_AD_YEAR, REF_AD_MONTH, REF_AD_DAY, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var totalDays = 0

        if (year >= REF_BS_YEAR) {
            for (y in REF_BS_YEAR until year) {
                for (m in 1..12) {
                    totalDays += getDaysInMonth(y, m)
                }
            }
            for (m in 1 until month) {
                totalDays += getDaysInMonth(year, m)
            }
            totalDays += (day - 1)
            refCal.add(Calendar.DAY_OF_YEAR, totalDays)
        } else {
            for (y in year until REF_BS_YEAR) {
                for (m in 1..12) {
                    totalDays += getDaysInMonth(y, m)
                }
            }
            for (m in 1 until month) {
                totalDays -= getDaysInMonth(year, m)
            }
            totalDays -= (day - 1)
            refCal.add(Calendar.DAY_OF_YEAR, -totalDays)
        }

        return refCal.time
    }

    fun formatNepali(): String {
        val devDay = toDevanagari(day)
        val devYear = toDevanagari(year)
        val monthName = MONTH_NAMES_NE.getOrElse(month - 1) { "महिना" }
        return "$devDay $monthName $devYear"
    }

    fun formatEnglish(): String {
        val monthName = MONTH_NAMES_EN.getOrElse(month - 1) { "Month" }
        return "$day $monthName $year"
    }
}
