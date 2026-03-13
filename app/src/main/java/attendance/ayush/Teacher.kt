package attendance.ayush

data class Teacher(
    val id: String,
    val name: String,
    var pin: String,
    val isPrincipal: Boolean = false,
    var lastPinChangeDate: Long? = null
)
