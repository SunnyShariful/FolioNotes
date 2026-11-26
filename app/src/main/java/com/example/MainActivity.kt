package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.NotesDbHelper
import com.example.data.local.SQLiteDataSource
import com.example.data.local.SharedPrefsDataSource
import com.example.data.repository.NotesRepository
import com.example.ui.notes.NotesScreen
import com.example.ui.notes.NotesViewModel
import com.example.ui.notes.NotesViewModelFactory
import com.example.ui.theme.FolioNotesTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val dbHelper = NotesDbHelper(applicationContext)
    val sqliteDataSource = SQLiteDataSource(dbHelper)
    val sharedPrefsDataSource = SharedPrefsDataSource(applicationContext)
    val repository = NotesRepository(sqliteDataSource, sharedPrefsDataSource)
    val viewModel =
      ViewModelProvider(this, NotesViewModelFactory(repository))[NotesViewModel::class.java]

    setContent {
      FolioNotesTheme {
        NotesScreen(viewModel = viewModel)
      }
    }
  }
}
