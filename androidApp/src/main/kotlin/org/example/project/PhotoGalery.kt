package org.example.project

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.example.project.model.Photo
import org.example.project.viewmodel.MediaViewModel

@Composable
fun PhotoGallery(
    viewModel: MediaViewModel,
    padding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<Photo?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val photo = pendingDelete
        if (result.resultCode == Activity.RESULT_OK && photo != null) {
            viewModel.onLocalPhotoRemoved(photo)
        } else {
            viewModel.onLocalDeleteCancelled()
        }
        pendingDelete = null
    }

    val onDelete: (Photo) -> Unit = { photo ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pendingDelete = photo
            runCatching {
                val request = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(Uri.parse(photo.uri))
                )
                deleteLauncher.launch(
                    IntentSenderRequest.Builder(request.intentSender).build()
                )
            }.onFailure { error ->
                pendingDelete = null
                viewModel.onLocalDeleteFailed(
                    error.message ?: "не удалось открыть системный диалог"
                )
            }
        } else {
            viewModel.deleteLocalPhoto(photo)
        }
    }

    val onOpen: (Photo) -> Unit = { photo ->
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(photo.uri), photo.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        state.errorMessage?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ошибка: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.refresh() }) {
                    Text("Повторить")
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                state.isLoading && state.photos.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                state.photos.isEmpty() -> {
                    Text(
                        text = "Фотографий нет",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.photos, key = { it.id }) { photo ->
                            PhotoCell(
                                photo = photo,
                                onClick = { onOpen(photo) },
                                onLongClick = { onDelete(photo) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PhotoCell(
    photo: Photo,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    AsyncImage(
        model = photo.uri,
        contentDescription = photo.displayName,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    )
}
