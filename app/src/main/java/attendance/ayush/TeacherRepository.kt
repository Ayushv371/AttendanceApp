package attendance.ayush

object TeacherRepository {
    private val _teachers = mutableListOf(
        Teacher("0", "Principal", "0000", isPrincipal = true),
        Teacher("1", "Teacher", "1111"),
    )

    val teachers: List<Teacher> get() = _teachers

    fun getTeacherById(id: String): Teacher? = _teachers.find { it.id == id }

    fun addTeacher(name: String, pin: String): Teacher {
        val nextId = if (_teachers.isEmpty()) "0" else (_teachers.maxOf { it.id.toInt() } + 1).toString()
        val newTeacher = Teacher(nextId, name, pin)
        _teachers.add(newTeacher)
        return newTeacher
    }
}
