package attendance.ayush

data class Teacher(
    val id: String,
    val name: String,
    var pin: String,
    val isManager: Boolean = false,
    var lastPinChangeDate: Long? = null
)
