package attendance.ayush

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

class AttendanceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun markAttendance(teacherId: String): Boolean {
        val today = dateFormat.format(Date())
        val attendanceSet = prefs.getStringSet(today, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        
        if (attendanceSet.contains(teacherId)) {
            return false // Already marked
        }
        
        attendanceSet.add(teacherId)
        prefs.edit().putStringSet(today, attendanceSet).apply()
        return true
    }

    fun getTodayAttendance(): List<String> {
        val today = dateFormat.format(Date())
        return prefs.getStringSet(today, emptySet())?.toList() ?: emptyList()
    }

    fun isAttendanceMarked(teacherId: String): Boolean {
        val today = dateFormat.format(Date())
        return prefs.getStringSet(today, emptySet())?.contains(teacherId) ?: false
    }
}
