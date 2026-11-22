package org.example.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.project.cloud.CloudSessionStore
import org.example.project.viewmodel.CloudDeleteState
import org.example.project.viewmodel.MediaViewModel
import org.koin.compose.koinInject

@Composable
fun Mail(
    viewModel: MediaViewModel,
    padding: PaddingValues
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionStore: CloudSessionStore = koinInject()
    val session by sessionStore.state.collectAsStateWithLifecycle()

    var showLogin by remember { mutableStateOf(false) }

    if (showLogin) {
        CloudLoginScreen(
            sessionStore = sessionStore,
            onClose = { showLogin = false }
        )
        return
    }

    if (!session.isLoggedIn) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Облако",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Аккаунт не подключён",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { showLogin = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Войти в облако")
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Облако",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Аккаунт: ${session.email}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = state.cloudPath,
            onValueChange = viewModel::onCloudPathChange,
            label = { Text("Путь в облаке") },
            placeholder = { Text("/photo.jpg") },
            supportingText = { Text("Полный путь от корня, например /photo.jpg") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { viewModel.removeCloudFile() },
            enabled = state.cloudPath.isNotBlank() &&
                    state.cloudDelete !is CloudDeleteState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Удалить в облаке")
        }

        when (val result = state.cloudDelete) {
            CloudDeleteState.Idle -> Unit

            CloudDeleteState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Удаляем...")
                }
            }

            is CloudDeleteState.Success -> {
                Text(
                    text = "Удалено: ${result.path}",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            is CloudDeleteState.Error -> {
                Text(
                    text = "Ошибка: ${result.message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (state.cloudDelete !is CloudDeleteState.Idle) {
            TextButton(onClick = viewModel::clearCloudDeleteState) {
                Text("Сбросить статус")
            }
        }

        TextButton(
            onClick = {
                sessionStore.clear()
                showLogin = true
            }
        ) {
            Text("Сменить аккаунт")
        }
    }
}
