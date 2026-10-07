package com.example.studytaskapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

data class StudyTask(
    val id: String = "",
    val title: String = "",
    val deadline: String = "",
    val isCompleted: Boolean = false
)

enum class Screen {
    HOME,
    ADD_TASK,
    DETAIL
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                StudyTaskApp()
            }
        }
    }
}

@Composable
fun StudyTaskApp() {

    var currentScreen by remember {
        mutableStateOf(Screen.HOME)
    }

    var selectedTask by remember {
        mutableStateOf<StudyTask?>(null)
    }

    val tasks = remember {
        mutableStateListOf<StudyTask>()
    }

    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(Unit) {

        db.collection("tasks")
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    return@addSnapshotListener
                }

                if (snapshot != null) {

                    tasks.clear()

                    for (document in snapshot.documents) {

                        val task = StudyTask(
                            id = document.id,
                            title = document.getString("title") ?: "",
                            deadline = document.getString("deadline") ?: "",
                            isCompleted =
                                document.getBoolean("isComplete") ?: false
                        )

                        tasks.add(task)
                    }
                }
            }
    }

    when (currentScreen) {

        Screen.HOME -> {

            HomeScreen(
                tasks = tasks,

                onAddTask = {
                    currentScreen = Screen.ADD_TASK
                },

                onTaskClick = { task ->
                    selectedTask = task
                    currentScreen = Screen.DETAIL
                }
            )
        }

        Screen.ADD_TASK -> {

            AddTaskScreen(

                onBack = {
                    currentScreen = Screen.HOME
                },

                onSave = { title, deadline ->

                    val taskData = hashMapOf(
                        "title" to title,
                        "deadline" to deadline,
                        "isComplete" to false
                    )

                    db.collection("tasks")
                        .add(taskData)
                        .addOnSuccessListener {

                            currentScreen = Screen.HOME
                        }
                }
            )
        }

        Screen.DETAIL -> {

            selectedTask?.let { task ->

                TaskDetailScreen(

                    task = task,

                    onBack = {
                        currentScreen = Screen.HOME
                    },

                    onMarkComplete = {

                        db.collection("tasks")
                            .document(task.id)
                            .update("isComplete", true)
                            .addOnSuccessListener {

                                selectedTask =
                                    task.copy(isCompleted = true)
                            }
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    tasks: List<StudyTask>,
    onAddTask: () -> Unit,
    onTaskClick: (StudyTask) -> Unit
) {

    val scope = rememberCoroutineScope()

    var aiRecommendation by remember {
        mutableStateOf("")
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    Scaffold { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            Text(
                text = "Study Tasks",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "Manage your study tasks",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Button(
                onClick = onAddTask,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add New Task")
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Button(
                onClick = {

                    val incompleteTasks =
                        tasks.filter { !it.isCompleted }

                    if (incompleteTasks.isEmpty()) {

                        aiRecommendation =
                            "No incomplete tasks available. You have completed all your tasks!"

                    } else {

                        isLoading = true

                        val taskList = incompleteTasks.joinToString(
                            separator = "\n"
                        ) { task ->

                            "- ${task.title} (Deadline: ${
                                task.deadline.ifBlank {
                                    "Not specified"
                                }
                            })"
                        }

                        val prompt = """
                            You are a study task assistant.

                            The student has the following incomplete study tasks:

                            $taskList

                            Choose exactly ONE task that the student should work on next.

                            Give the task name and one short reason.

                            Do not recommend a task that is already completed.

                            Keep the response very short.

                            Use exactly this format:

                            Recommended Task: [task name]
                            Reason: [one short reason]
                        """.trimIndent()

                        scope.launch {

                            try {

                                val model =
                                    Firebase.ai(
                                        backend =
                                            GenerativeBackend.googleAI()
                                    ).generativeModel(
                                        "gemini-3.8-flash"
                                    )

                                val response =
                                    model.generateContent(prompt)

                                aiRecommendation =
                                    response.text
                                        ?: "No recommendation received."

                            } catch (e: Exception) {

                                aiRecommendation =
                                    "AI error: ${e.message}"
                            }

                            isLoading = false
                        }
                    }
                },

                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Suggest my next task")
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            if (isLoading) {

                Text(
                    text = "AI is thinking...",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )
            }

            if (aiRecommendation.isNotEmpty()) {

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {

                        Text(
                            text = "AI Recommendation",
                            style =
                                MaterialTheme.typography.titleMedium
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Text(
                            text = aiRecommendation,
                            style =
                                MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(16.dp)
                )
            }

            if (tasks.isEmpty()) {

                Text(
                    text = "No study tasks available.",
                    style = MaterialTheme.typography.bodyLarge
                )

            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    items(
                        items = tasks,
                        key = { task -> task.id }
                    ) { task ->

                        TaskCard(
                            task = task,
                            onClick = {
                                onTaskClick(task)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskCard(
    task: StudyTask,
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = task.title,
                style =
                    MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            if (task.deadline.isNotBlank()) {

                Text(
                    text = "Deadline: ${task.deadline}"
                )

            } else {

                Text(
                    text = "Deadline: Not specified"
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = if (task.isCompleted) {
                    "Status: Completed"
                } else {
                    "Status: Pending"
                }
            )
        }
    }
}

@Composable
fun AddTaskScreen(
    onBack: () -> Unit,
    onSave: (String, String) -> Unit
) {

    var title by remember {
        mutableStateOf("")
    }

    var deadline by remember {
        mutableStateOf("")
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    Scaffold { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            Text(
                text = "Add Task",
                style =
                    MaterialTheme.typography.headlineMedium
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "Create a new study task"
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            OutlinedTextField(
                value = title,

                onValueChange = {
                    title = it
                    errorMessage = ""
                },

                label = {
                    Text("Task Title")
                },

                placeholder = {
                    Text("Enter task title")
                },

                singleLine = true,

                modifier = Modifier.fillMaxWidth()
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            OutlinedTextField(
                value = deadline,

                onValueChange = {
                    deadline = it
                },

                label = {
                    Text("Deadline (Optional)")
                },

                placeholder = {
                    Text("Enter deadline")
                },

                singleLine = true,

                modifier = Modifier.fillMaxWidth()
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            if (errorMessage.isNotEmpty()) {

                Text(
                    text = errorMessage,
                    color =
                        MaterialTheme.colorScheme.error,
                    style =
                        MaterialTheme.typography.bodyMedium
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {

                        if (title.trim().isEmpty()) {

                            errorMessage =
                                "Please enter a task title."

                        } else {

                            onSave(
                                title.trim(),
                                deadline.trim()
                            )
                        }
                    },

                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Task")
                }
            }
        }
    }
}

@Composable
fun TaskDetailScreen(
    task: StudyTask,
    onBack: () -> Unit,
    onMarkComplete: () -> Unit
) {

    Scaffold { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            Text(
                text = "Task Detail",
                style =
                    MaterialTheme.typography.headlineMedium
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Text(
                        text = task.title,
                        style =
                            MaterialTheme.typography.headlineSmall
                    )

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    Text(
                        text = if (task.deadline.isNotBlank()) {
                            "Deadline: ${task.deadline}"
                        } else {
                            "Deadline: Not specified"
                        }
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Text(
                        text = if (task.isCompleted) {
                            "Status: Completed"
                        } else {
                            "Status: Pending"
                        }
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            if (!task.isCompleted) {

                Button(
                    onClick = onMarkComplete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mark as Complete")
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )
            }

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Home")
            }
        }
    }
}