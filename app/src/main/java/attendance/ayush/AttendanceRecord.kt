package attendance.ayush

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val teacherId: String,
    val date: String, // yyyy-MM-dd
    val inTime: String?,
    val outTime: String?,
    val inPhotoPath: String?,
    val outPhotoPath: String?,
    val timestamp: Long
)
