package attendance.ayush

import androidx.room.*

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records WHERE teacherId = :teacherId AND date = :date")
    suspend fun getRecordForTeacherOnDate(teacherId: String, date: String): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    suspend fun getAllRecordsOnDate(date: String): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE teacherId = :teacherId")
    suspend fun getAllRecordsForTeacher(teacherId: String): List<AttendanceRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AttendanceRecord)

    @Update
    suspend fun updateRecord(record: AttendanceRecord)

    @Query("DELETE FROM attendance_records WHERE timestamp < :threshold")
    suspend fun deleteOldRecords(threshold: Long)
    
    @Query("SELECT * FROM attendance_records WHERE timestamp < :threshold")
    suspend fun getOldRecords(threshold: Long): List<AttendanceRecord>
}
