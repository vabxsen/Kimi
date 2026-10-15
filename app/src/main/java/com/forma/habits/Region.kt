package com.forma.habits

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.TimeZone

/**
 * Where the phone is, as far as the calendar cares: which half of the world (so the seasons are
 * the ones outside the window) and which day its weeks begin on. Both are read from the phone's
 * own settings once, when first needed, and never leave it.
 */
object Region {
    val south: Boolean by lazy { isSouthern(TimeZone.getDefault().id) }
    val firstDay: DayOfWeek by lazy { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
}

/** Time zones south of the tropics, plus the tropical ones whose seasons still follow the south. */
private val SouthernZones = setOf(
    "America/Argentina/Buenos_Aires", "America/Buenos_Aires", "America/Cordoba", "America/Mendoza", "America/Catamarca",
    "America/Jujuy", "America/Rosario", "America/Santiago", "America/Punta_Arenas", "America/Montevideo",
    "America/Asuncion", "America/Sao_Paulo", "America/Bahia", "America/Recife", "America/Maceio", "America/Fortaleza",
    "America/Araguaina", "America/Cuiaba", "America/Campo_Grande", "America/Porto_Velho", "America/Rio_Branco",
    "America/Eirunepe", "America/Manaus", "America/Belem", "America/Santarem", "America/Noronha", "America/La_Paz",
    "America/Lima", "Brazil/East", "Brazil/West", "Brazil/Acre", "Brazil/DeNoronha", "Chile/Continental",
    "Chile/EasterIsland", "Pacific/Easter", "Pacific/Galapagos",
    "Africa/Johannesburg", "Africa/Maseru", "Africa/Mbabane", "Africa/Windhoek", "Africa/Gaborone", "Africa/Maputo",
    "Africa/Harare", "Africa/Lusaka", "Africa/Blantyre", "Africa/Lubumbashi", "Africa/Luanda", "Africa/Dar_es_Salaam",
    "Africa/Kigali", "Africa/Bujumbura", "Africa/Kinshasa", "Africa/Brazzaville",
    "Indian/Antananarivo", "Indian/Mauritius", "Indian/Reunion", "Indian/Mayotte", "Indian/Comoro", "Indian/Kerguelen",
    "Indian/Cocos", "Indian/Christmas",
    "Pacific/Auckland", "Pacific/Chatham", "Pacific/Norfolk", "Pacific/Fiji", "Pacific/Tongatapu", "Pacific/Apia",
    "Pacific/Noumea", "Pacific/Efate", "Pacific/Rarotonga", "Pacific/Tahiti", "Pacific/Pitcairn", "Pacific/Niue",
    "Pacific/Wallis", "Pacific/Fakaofo", "Pacific/Funafuti", "Pacific/Port_Moresby", "Pacific/Guadalcanal",
    "Pacific/Gambier", "Pacific/Marquesas", "Asia/Jakarta", "Asia/Makassar", "Asia/Jayapura", "Asia/Dili",
    "Atlantic/Stanley", "Atlantic/South_Georgia", "Atlantic/St_Helena", "NZ", "NZ-CHAT"
)

/** Whether the [zone] (an IANA id such as "Australia/Sydney") lies in the southern hemisphere. */
fun isSouthern(zone: String): Boolean =
    zone.startsWith("Australia/") || zone.startsWith("Antarctica/") || zone.startsWith("America/Argentina/") || zone in SouthernZones

/**
 * Weeks begin on the region's first day. Until this version every week began on a Monday, so
 * weeks before [RegionWeeksFrom] keep that shape and the past (and its perfect weeks and freezes)
 * never shifts; the week that straddles the change is simply a little shorter.
 */
internal val RegionWeeksFrom: LocalDate = LocalDate.of(2026, 10, 19)

/** The first day of the week [date] falls in. */
internal fun weekStart(date: LocalDate, first: DayOfWeek = Region.firstDay): LocalDate =
    if (date.isBefore(RegionWeeksFrom)) date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    else maxOf(date.with(TemporalAdjusters.previousOrSame(first)), RegionWeeksFrom)

/** Whether [date] is the last day of its week. */
internal fun endsWeek(date: LocalDate, first: DayOfWeek = Region.firstDay): Boolean =
    weekStart(date.plusDays(1), first) != weekStart(date, first)

/** Every day of the week that begins on [start]: seven, apart from the one shorter week at the change. */
internal fun weekOf(start: LocalDate, first: DayOfWeek = Region.firstDay): List<LocalDate> =
    generateSequence(start) { it.plusDays(1) }.take(7).takeWhile { weekStart(it, first) == start }.toList()
