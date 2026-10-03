package com.bhrikuty.dokodocs.core.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhrikuty.dokodocs.theme.PrimaryLight
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DualCalendarPickerDialog(
    initialDate: Date = Date(),
    defaultToBs: Boolean = true,
    onDateSelected: (Date) -> Unit,
    onDismissRequest: () -> Unit
) {
    var isBsMode by remember { mutableStateOf(defaultToBs) }

    // BS State
    val initialNepaliDate = remember { NepaliDate.fromDate(initialDate) }
    var selectedBsYear by remember { mutableIntStateOf(initialNepaliDate.year) }
    var selectedBsMonth by remember { mutableIntStateOf(initialNepaliDate.month) }
    var selectedBsDay by remember { mutableIntStateOf(initialNepaliDate.day) }
    var showYearDropdown by remember { mutableStateOf(false) }

    // AD State
    val initialCal = remember { Calendar.getInstance().apply { time = initialDate } }
    var selectedAdYear by remember { mutableIntStateOf(initialCal.get(Calendar.YEAR)) }
    var selectedAdMonth by remember { mutableIntStateOf(initialCal.get(Calendar.MONTH)) } // 0-indexed
    var selectedAdDay by remember { mutableIntStateOf(initialCal.get(Calendar.DAY_OF_MONTH)) }

    // Compute live converted dates
    val currentSelectedDate: Date = if (isBsMode) {
        val maxDays = NepaliDate.getDaysInMonth(selectedBsYear, selectedBsMonth)
        val validDay = selectedBsDay.coerceIn(1, maxDays)
        NepaliDate(selectedBsYear, selectedBsMonth, validDay).toGregorianDate()
    } else {
        Calendar.getInstance().apply {
            set(selectedAdYear, selectedAdMonth, selectedAdDay.coerceIn(1, 31), 12, 0, 0)
        }.time
    }

    val liveBsDate = remember(currentSelectedDate) { NepaliDate.fromDate(currentSelectedDate) }
    val adFormat = remember { SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header with Calendar Mode Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBsMode) "नेपाली पात्रो (BS)" else "Gregorian (AD)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedButton(
                        onClick = { isBsMode = !isBsMode },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isBsMode) "Switch to AD" else "Switch to BS", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Dual Live Date Display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "वि.सं: ${liveBsDate.formatNepali()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "AD: ${adFormat.format(currentSelectedDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                if (isBsMode) {
                    // BS Month & Year Header Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedBsMonth > 1) {
                                    selectedBsMonth--
                                } else if (selectedBsYear > 2060) {
                                    selectedBsYear--
                                    selectedBsMonth = 12
                                }
                            }
                        ) {
                            Icon(Icons.Default.ChevronLeft, "Prev Month")
                        }

                        // Year & Month Clickable Picker
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${NepaliDate.MONTH_NAMES_NE[selectedBsMonth - 1]} (${NepaliDate.MONTH_NAMES_EN[selectedBsMonth - 1]})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box {
                                Text(
                                    text = "${NepaliDate.toDevanagari(selectedBsYear)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryLight,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PrimaryLight.copy(alpha = 0.12f))
                                        .clickable { showYearDropdown = true }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )

                                DropdownMenu(
                                    expanded = showYearDropdown,
                                    onDismissRequest = { showYearDropdown = false }
                                ) {
                                    (2075..2090).forEach { year ->
                                        DropdownMenuItem(
                                            text = { Text("${NepaliDate.toDevanagari(year)} ($year BS)") },
                                            onClick = {
                                                selectedBsYear = year
                                                showYearDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                if (selectedBsMonth < 12) {
                                    selectedBsMonth++
                                } else if (selectedBsYear < 2095) {
                                    selectedBsYear++
                                    selectedBsMonth = 1
                                }
                            }
                        ) {
                            Icon(Icons.Default.ChevronRight, "Next Month")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Day of Week Header
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        listOf("आइत", "सोम", "मंगल", "बुध", "बिही", "शुक्र", "शनि").forEach { dayName ->
                            Text(
                                text = dayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (dayName == "शनि") Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Days Grid for BS Month
                    val daysInBsMonth = NepaliDate.getDaysInMonth(selectedBsYear, selectedBsMonth)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        items(daysInBsMonth) { index ->
                            val dayNumber = index + 1
                            val isSelected = selectedBsDay == dayNumber
                            Box(
                                modifier = Modifier
                                    .padding(3.dp)
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) PrimaryLight else Color.Transparent)
                                    .clickable { selectedBsDay = dayNumber },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = NepaliDate.toDevanagari(dayNumber),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    // AD Calendar Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedAdMonth > 0) {
                                    selectedAdMonth--
                                } else {
                                    selectedAdYear--
                                    selectedAdMonth = 11
                                }
                            }
                        ) {
                            Icon(Icons.Default.ChevronLeft, "Prev Month")
                        }

                        val adMonthName = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(
                            Calendar.getInstance().apply { set(selectedAdYear, selectedAdMonth, 1) }.time
                        )
                        Text(
                            text = adMonthName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = {
                                if (selectedAdMonth < 11) {
                                    selectedAdMonth++
                                } else {
                                    selectedAdYear++
                                    selectedAdMonth = 0
                                }
                            }
                        ) {
                            Icon(Icons.Default.ChevronRight, "Next Month")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Day of Week Header
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { dayName ->
                            Text(
                                text = dayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (dayName == "Sat") Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val cal = Calendar.getInstance().apply { set(selectedAdYear, selectedAdMonth, 1) }
                    val maxAdDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        items(maxAdDays) { index ->
                            val dayNum = index + 1
                            val isSelected = selectedAdDay == dayNum
                            Box(
                                modifier = Modifier
                                    .padding(3.dp)
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) PrimaryLight else Color.Transparent)
                                    .clickable { selectedAdDay = dayNum },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayNum.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDateSelected(currentSelectedDate)
                    onDismissRequest()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
            ) {
                Text("Select Date", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
