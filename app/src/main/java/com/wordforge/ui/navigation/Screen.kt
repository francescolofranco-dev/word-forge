package com.wordforge.ui.navigation

sealed class Screen(val route: String) {
    object WordList : Screen("word_list")
    object AddWord : Screen("add_word")
    object HowItWorks : Screen("how_it_works")
    object OverdueReview : Screen("overdue_review")
    object LlmSettings : Screen("llm_settings")
    object ExerciseSetup : Screen("exercise_setup")
    object ExerciseSession : Screen("exercise_session")
    object Quiz : Screen("quiz/{wordId}") {
        fun createRoute(wordId: String) = "quiz/$wordId"
    }
    object WordDetail : Screen("word_detail/{wordId}") {
        fun createRoute(wordId: String) = "word_detail/$wordId"
    }
    object EditWord : Screen("edit_word/{wordId}") {
        fun createRoute(wordId: String) = "edit_word/$wordId"
    }
}
