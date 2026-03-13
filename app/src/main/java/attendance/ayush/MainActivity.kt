package attendance.ayush

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import attendance.ayush.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var currentTeacher: Teacher? = null
    private lateinit var db: AppDatabase
    private var isFaceDetected = false
    private var isCheckInMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupProfileSpinner()
        setupCalendar()
        cleanupOldRecords()

        binding.btnCheckIn.setOnClickListener {
            prepareAttendance(true)
        }

        binding.btnCheckOut.setOnClickListener {
            prepareAttendance(false)
        }

        binding.btnCapture.setOnClickListener {
            val teacher = currentTeacher
            if (teacher == null) return@setOnClickListener
            
            if (isFaceDetected) {
                showPinDialog(teacher)
            } else {
                Toast.makeText(this, "Face not detected! Please align properly.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBackFromCamera.setOnClickListener {
            binding.cameraOverlay.visibility = View.GONE
        }

        binding.btnChangePin.setOnClickListener {
            showChangePinSelectionDialog()
        }

        binding.btnAddTeacher.setOnClickListener {
            showAdminVerificationDialog()
        }
    }

    private fun setupProfileSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, TeacherRepository.teachers.map { it.name })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.profileSpinner.adapter = adapter

        binding.profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTeacher = TeacherRepository.teachers[position]
                updateUIForSelectedTeacher()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateUIForSelectedTeacher() {
        val teacher = currentTeacher ?: return
        binding.btnAddTeacher.visibility = if (teacher.isPrincipal) View.VISIBLE else View.GONE
        binding.cameraOverlay.visibility = View.GONE
        
        // Reset status to avoid showing previous teacher's data while loading
        binding.statusIn.text = "In: --"
        binding.statusOut.text = "Out: --"
        binding.btnCheckIn.isEnabled = false
        binding.btnCheckOut.isEnabled = false

        lifecycleScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val record = db.attendanceDao().getRecordForTeacherOnDate(teacher.id, today)
            
            // Verify selection hasn't changed during fetch
            if (currentTeacher?.id == teacher.id) {
                binding.statusIn.text = "In: ${record?.inTime ?: "--"}"
                binding.statusOut.text = "Out: ${record?.outTime ?: "--"}"
                
                binding.btnCheckIn.isEnabled = record?.inTime == null
                binding.btnCheckOut.isEnabled = record?.inTime != null && record.outTime == null
            }
        }
    }

    private fun setupCalendar() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val date = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            showRecordForDate(date)
        }
    }

    private fun showRecordForDate(date: String) {
        val current = currentTeacher ?: return
        lifecycleScope.launch {
            if (current.isPrincipal) {
                val records = db.attendanceDao().getAllRecordsOnDate(date)
                showPrincipalReport(date, records)
            } else {
                val record = db.attendanceDao().getRecordForTeacherOnDate(current.id, date)
                showTeacherRecordDialog(current, date, record)
            }
        }
    }

    private fun showTeacherRecordDialog(teacher: Teacher, date: String, record: AttendanceRecord?) {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            setBackgroundColor(Color.WHITE)
        }
        scroll.addView(layout)

        if (record != null) {
            layout.addView(TextView(this).apply { 
                text = "Check-In: ${record.inTime}"
                textSize = 18f
                setTextColor(Color.BLACK)
            })
            record.inPhotoPath?.let { path ->
                val img = ImageView(this)
                img.setImageBitmap(BitmapFactory.decodeFile(path))
                img.layoutParams = LinearLayout.LayoutParams(600, 600)
                layout.addView(img)
            }

            layout.addView(TextView(this).apply { 
                text = "\nCheck-Out: ${record.outTime ?: "N/A"}"
                textSize = 18f
                setPadding(0, 20, 0, 0)
                setTextColor(Color.BLACK)
            })
            record.outPhotoPath?.let { path ->
                val img = ImageView(this)
                img.setImageBitmap(BitmapFactory.decodeFile(path))
                img.layoutParams = LinearLayout.LayoutParams(600, 600)
                layout.addView(img)
            }
        } else {
            layout.addView(TextView(this).apply { 
                text = "No records found."
                setTextColor(Color.BLACK)
            })
        }

        AlertDialog.Builder(this)
            .setTitle("${teacher.name} - $date")
            .setView(scroll)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPrincipalReport(date: String, records: List<AttendanceRecord>) {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 20)
            setBackgroundColor(Color.WHITE)
        }
        scroll.addView(container)

        if (records.isEmpty()) {
            container.addView(TextView(this).apply { 
                text = "No attendance marked for today."
                setTextColor(Color.BLACK)
            })
        } else {
            records.forEach { record ->
                val teacher = TeacherRepository.getTeacherById(record.teacherId)
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(10, 10, 10, 30)
                    background = ContextCompat.getDrawable(context, android.R.drawable.dialog_holo_light_frame)
                }
                card.addView(TextView(this).apply { 
                    text = "${teacher?.name ?: "Unknown (ID: ${record.teacherId})"}\nIn: ${record.inTime} | Out: ${record.outTime ?: "--"}"
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.BLACK)
                })

                val photoLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                record.inPhotoPath?.let {
                    val img = ImageView(this).apply {
                        setImageBitmap(BitmapFactory.decodeFile(it))
                        layoutParams = LinearLayout.LayoutParams(300, 300)
                    }
                    photoLayout.addView(img)
                }
                record.outPhotoPath?.let {
                    val img = ImageView(this).apply {
                        setImageBitmap(BitmapFactory.decodeFile(it))
                        layoutParams = LinearLayout.LayoutParams(300, 300).apply { leftMargin = 10 }
                    }
                    photoLayout.addView(img)
                }
                card.addView(photoLayout)

                container.addView(card)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("All Attendance - $date")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun prepareAttendance(isCheckIn: Boolean) {
        isCheckInMode = isCheckIn
        if (allPermissionsGranted()) {
            binding.cameraOverlay.visibility = View.VISIBLE
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val imageAnalyzer = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(cameraExecutor, FaceAnalyzer { detected ->
                    isFaceDetected = detected
                    runOnUiThread {
                        binding.faceStatusText.text = if (detected) "Face detected! Ready to capture." else "Align face to capture."
                    }
                })
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e("AttendanceApp", "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showPinDialog(teacher: Teacher) {
        val input = EditText(this).apply {
            hint = "Enter PIN"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Confirm Identity")
            .setMessage("Enter PIN for ${teacher.name}")
            .setView(input)
            .setPositiveButton("Capture & Save") { _, _ ->
                val pin = input.text.toString()
                val principal = TeacherRepository.teachers.find { it.isPrincipal }
                // Master PIN logic or User's own PIN
                if (pin == teacher.pin || pin == principal?.pin) {
                    capturePhoto(teacher)
                } else {
                    Toast.makeText(this, "Incorrect PIN!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showChangePinSelectionDialog() {
        val teacher = currentTeacher ?: return
        if (teacher.isPrincipal) {
            val teacherNames = TeacherRepository.teachers.map { it.name }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Select Profile to Change PIN")
                .setItems(teacherNames) { _, which ->
                    val selectedTeacher = TeacherRepository.teachers[which]
                    if (selectedTeacher.isPrincipal || selectedTeacher.id == teacher.id) {
                        showTeacherChangePinDialog(selectedTeacher)
                    } else {
                        showAdminResetPinDialog(selectedTeacher)
                    }
                }
                .show()
        } else {
            showTeacherChangePinDialog(teacher)
        }
    }

    private fun showTeacherChangePinDialog(teacher: Teacher) {
        // Restriction: Once a month
        val lastChange = teacher.lastPinChangeDate ?: 0L
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = lastChange
        val currentCalendar = Calendar.getInstance()
        
        if (lastChange != 0L && 
            calendar.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH) &&
            calendar.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR)) {
            Toast.makeText(this, "You can only change your PIN once every month.", Toast.LENGTH_LONG).show()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
            setBackgroundColor(Color.WHITE)
        }
        val oldPinInput = EditText(this).apply { 
            hint = "Current PIN"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD 
        }
        val newPinInput = EditText(this).apply { 
            hint = "New 4-digit PIN"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD 
        }
        
        layout.addView(oldPinInput)
        layout.addView(newPinInput)

        AlertDialog.Builder(this)
            .setTitle("Change PIN for ${teacher.name}")
            .setView(layout)
            .setPositiveButton("Change") { _, _ ->
                if (oldPinInput.text.toString() == teacher.pin) {
                    val newPin = newPinInput.text.toString()
                    if (newPin.length == 4) {
                        teacher.pin = newPin
                        teacher.lastPinChangeDate = System.currentTimeMillis()
                        Toast.makeText(this, "PIN changed successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "PIN must be 4 digits!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Current PIN is incorrect!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAdminResetPinDialog(teacher: Teacher) {
        val input = EditText(this).apply {
            hint = "New PIN for ${teacher.name}"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Admin Reset: ${teacher.name}")
            .setMessage("Set new 4-digit PIN for ${teacher.name}")
            .setView(input)
            .setPositiveButton("Reset") { _, _ ->
                val newPin = input.text.toString()
                if (newPin.length == 4) {
                    teacher.pin = newPin
                    Toast.makeText(this, "PIN for ${teacher.name} reset successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "PIN must be 4 digits!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun capturePhoto(teacher: Teacher) {
        val imageCapture = imageCapture ?: return
        val photoFile = File(filesDir, "ATT_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                saveAttendance(teacher, photoFile.absolutePath)
            }
            override fun onError(exc: ImageCaptureException) {
                Toast.makeText(this@MainActivity, "Photo capture failed!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveAttendance(teacher: Teacher, photoPath: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        lifecycleScope.launch {
            val dao = db.attendanceDao()
            val existingRecord = dao.getRecordForTeacherOnDate(teacher.id, today)

            if (isCheckInMode) {
                if (existingRecord == null) {
                    dao.insertRecord(AttendanceRecord(
                        teacherId = teacher.id,
                        date = today,
                        inTime = time,
                        outTime = null,
                        inPhotoPath = photoPath,
                        outPhotoPath = null,
                        timestamp = System.currentTimeMillis()
                    ))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Check-In successful for ${teacher.name}!", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                if (existingRecord != null && existingRecord.outTime == null) {
                    val updatedRecord = existingRecord.copy(
                        outTime = time,
                        outPhotoPath = photoPath
                    )
                    dao.updateRecord(updatedRecord)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Check-Out successful for ${teacher.name}!", Toast.LENGTH_LONG).show()
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                binding.cameraOverlay.visibility = View.GONE
                updateUIForSelectedTeacher()
            }
        }
    }

    private fun showAdminVerificationDialog() {
        val input = EditText(this).apply {
            hint = "Principal PIN"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Admin Access")
            .setMessage("Verify Principal Omprakash's PIN to add teachers.")
            .setView(input)
            .setPositiveButton("Verify") { _, _ ->
                val principal = TeacherRepository.teachers.find { it.isPrincipal }
                if (input.text.toString() == principal?.pin) {
                    showAddTeacherDialog()
                } else {
                    Toast.makeText(this, "Invalid Admin PIN!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddTeacherDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
            setBackgroundColor(Color.WHITE)
        }
        val nameInput = EditText(this).apply { 
            hint = "Full Name"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
        }
        val pinInput = EditText(this).apply { 
            hint = "Set PIN (4-digit)"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        
        layout.addView(nameInput)
        layout.addView(pinInput)

        AlertDialog.Builder(this)
            .setTitle("Add New Teacher")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString()
                val pin = pinInput.text.toString()
                if (name.isNotEmpty() && pin.isNotEmpty()) {
                    TeacherRepository.addTeacher(name, pin)
                    setupProfileSpinner()
                    Toast.makeText(this, "Teacher added successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "All fields are required!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cleanupOldRecords() {
        lifecycleScope.launch {
            val fortyFiveDaysMillis = 45L * 24 * 60 * 60 * 1000
            val threshold = System.currentTimeMillis() - fortyFiveDaysMillis
            val oldRecords = db.attendanceDao().getOldRecords(threshold)
            
            oldRecords.forEach { record ->
                record.inPhotoPath?.let { File(it).delete() }
                record.outPhotoPath?.let { File(it).delete() }
            }
            db.attendanceDao().deleteOldRecords(threshold)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) startCamera()
            else Toast.makeText(this, "Camera permission required.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
