package app.mystery0.nodeflow.core.datastore

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.nodeFlowDataStore by preferencesDataStore(name = "nodeflow_preferences")
